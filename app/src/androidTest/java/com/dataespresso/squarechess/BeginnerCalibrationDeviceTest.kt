package com.dataespresso.squarechess

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.github.bhlangonijr.chesslib.Side
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Offline test harness only. No alternative move-selection logic is added to the app. */
class BeginnerCalibrationDeviceTest {
    private suspend fun search(fen: String, moves: List<String>, level: EngineLevel) = withContext(Dispatchers.IO) {
        NativeEngine.search(fen,moves.joinToString(" "),level.skill,level.multiPv,ENGINE_MOVE_TIME_MS)
    }
    @Test fun playCalibrationGamesAndTacticalProbes() = runBlocking {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("beginnerCalibration")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(BuildConfig.FAIRY_ENGINE && isIsolatedTestPackage(context.packageName))
        val folder=File(context.filesDir,"beginner-calibration").apply {mkdirs()}
        val engine=StockfishController(context)
        val wanted=args.getString("levels")?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
        val candidates=ENGINE_LEVELS.filter { wanted?.contains(it.level) ?: (it.level<=4) }
        val reference=engineLevel(args.getString("reference")?.toIntOrNull() ?: 5)
        // Extra rounds repeat the games only, for long reliability runs.
        val rounds=args.getString("rounds")?.toIntOrNull() ?: 1
        val probes=listOf(
            "free_queen" to "4k3/8/8/8/3q4/8/3R4/4K3 w - - 0 1",
            "mate_in_one" to "7k/5K2/6Q1/8/8/8/8/8 w - - 0 1",
            "fools_mate" to ChessPosition(moves=listOf("f2f3","e7e5","g2g4")).board.fen
        )
        try {
            engine.start()
            File(folder,"probes.csv").bufferedWriter().use {out ->
                out.appendLine("level,skill,multipv,probe,repeat,move,captured_queen,checkmate,elapsed_ms")
                for(level in candidates) for((name,fen) in probes) repeat(12) {rep ->
                    engine.newGame()
                    val start=SystemClock.elapsedRealtime()
                    val best=search(fen,emptyList(),level)
                    assertEquals("skill=${level.skill};multipv=${level.multiPv};threads=1;hash=16;nnue=0;limitStrength=0",NativeEngine.configuration())
                    val position=ChessPosition(fen)
                    val move=position.resolve(best)
                    assertNotNull("Illegal probe move",move)
                    val next=position.append(move!!)
                    out.appendLine("${level.level},${level.skill},${level.multiPv},$name,$rep,$best,${name=="free_queen" && best=="d2d4"},${next.board.isMated},${SystemClock.elapsedRealtime()-start}")
                    out.flush()
                }
            }
            File(folder,"games.csv").bufferedWriter().use {out ->
                out.appendLine("round,level,skill,multipv,reference_level,candidate_side,scenario,result,reason,plies,elapsed_ms")
                for(round in 1..rounds) for(level in candidates) for(candidateWhite in listOf(true,false)) {
                    // The reference starts with a fixed, legal poor opening; afterward both
                    // sides use the unmodified engine. This is a recovery scenario, not a human model.
                    val opening=if(candidateWhite) listOf("e2e4","a7a5","d2d4","h7h5")
                        else listOf("a2a4","e7e5","h2h4","d7d5")
                    var position=ChessPosition(moves=opening)
                    engine.newGame()
                    val began=SystemClock.elapsedRealtime()
                    val name="r$round-level${level.level}-${if(candidateWhite) "white" else "black"}"
                    while(position.automaticResult()==null && !position.canClaimDraw() && position.moves.size<400) {
                        val candidateTurn=(position.board.sideToMove==Side.WHITE)==candidateWhite
                        val best=search(START_FEN,position.moves,if(candidateTurn) level else reference)
                        val move=position.resolve(best)
                        if(move==null) File(folder,"failure-$name.txt").writeText("best=$best\nfen=${position.board.fen}\nhistory=${position.moves.joinToString(" ")}\nlegal=${position.legal.joinToString(" ")}\n${NativeEngine.metrics()}")
                        assertNotNull("Rejected engine response $best in $name at ${position.moves.size}",move)
                        // The bridge discards and reports output left over from earlier commands.
                        val stale=NativeEngine.metrics().lines().filter {it.startsWith("stale:")}
                        if(stale.isNotEmpty()) File(folder,"stale-$name-${position.moves.size}.txt").writeText(stale.joinToString("\n"))
                        assertTrue("Stale engine output in $name at ${position.moves.size}: $stale",stale.isEmpty())
                        position=position.append(move!!)
                        File(folder,"progress.txt").writeText("game=$name\nplies=${position.moves.size}\nfen=${position.board.fen}\nhistory=${position.moves.joinToString(" ")}\n")
                    }
                    val result=position.automaticResult() ?: if(position.canClaimDraw()) "1/2-1/2" else "*"
                    val reason=position.automaticResultReason() ?: if(position.canClaimDraw()) "Claimable draw claimed by harness" else "400-ply test limit"
                    out.appendLine("$round,${level.level},${level.skill},${level.multiPv},${reference.level},${if(candidateWhite) "white" else "black"},opponent_pawn_flanks,$result,$reason,${position.moves.size},${SystemClock.elapsedRealtime()-began}")
                    out.flush()
                    val san=position.san.chunked(2).mapIndexed {i,pair -> "${i+1}. ${pair.joinToString(" ")}"}.joinToString(" ")
                    val candidateName="Level ${level.level}";val referenceName="Level ${reference.level}"
                    File(folder,"$name.pgn").writeText("[Event \"Square Chess automated calibration\"]\n[White \"${if(candidateWhite) candidateName else referenceName}\"]\n[Black \"${if(candidateWhite) referenceName else candidateName}\"]\n[Result \"$result\"]\n\n$san $result\n")
                    File(folder,"$name.uci.txt").writeText(position.moves.joinToString(" "))
                }
            }
        } finally {engine.close()}
    }
}

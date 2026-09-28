package com.chriotte.squarechess

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.github.bhlangonijr.chesslib.Side
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Offline test harness only. No alternative move-selection logic is added to the app. */
class BeginnerCalibrationDeviceTest {
    @Test fun investigateRejectedPosition() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("reproduceRejection")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(BuildConfig.FAIRY_ENGINE && context.packageName=="com.chriotte.squarechess.fairyexperiment")
        val engine=StockfishController(context)
        val fen="rnb1kbnr/1p2ppp1/2pq4/p2p3p/3PP3/2N3N1/PPP2PPP/R1BQKB1R w KQkq - 0 6"
        val folder=File(context.filesDir,"beginner-calibration-repro").apply {mkdirs()}
        try {
            engine.start()
            repeat(30) {i ->
                engine.newGame()
                val best=engine.search(fen,emptyList(),12)
                File(folder,"probe-$i.txt").writeText("best=$best\n${NativeEngine.metrics()}")
                assertNotNull("Rejected response $best at repeat $i",ChessPosition(fen).resolve(best))
            }
        } finally {engine.close()}
    }
    @Test fun playCalibrationGamesAndTacticalProbes() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("beginnerCalibration")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(BuildConfig.FAIRY_ENGINE && context.packageName=="com.chriotte.squarechess.fairyexperiment")
        val folder=File(context.filesDir,"beginner-calibration").apply {mkdirs()}
        val engine=StockfishController(context)
        val args=InstrumentationRegistry.getArguments()
        val candidates=listOf(10 to -3,11 to -2,12 to -1,6 to 0).filter {args.getString("skills")?.split(",")?.contains(it.second.toString()) ?: true}
        val probes=listOf(
            "free_queen" to "4k3/8/8/8/3q4/8/3R4/4K3 w - - 0 1",
            "mate_in_one" to "7k/5K2/6Q1/8/8/8/8/8 w - - 0 1",
            "fools_mate" to ChessPosition(moves=listOf("f2f3","e7e5","g2g4")).board.fen
        )
        try {
            engine.start()
            File(folder,"probes.csv").bufferedWriter().use {out ->
                out.appendLine("skill,probe,repeat,move,captured_queen,checkmate,elapsed_ms")
                for((id,skill) in candidates) for((name,fen) in probes) repeat(12) {rep ->
                    engine.newGame()
                    val start=SystemClock.elapsedRealtime()
                    val best=engine.search(fen,emptyList(),id)
                    assertEquals("skill=$skill;multipv=8;threads=1;hash=16;nnue=0;limitStrength=0",NativeEngine.configuration())
                    val position=ChessPosition(fen)
                    val move=position.resolve(best)
                    assertNotNull("Illegal probe move",move)
                    val next=position.append(move!!)
                    out.appendLine("$skill,$name,$rep,$best,${name=="free_queen" && best=="d2d4"},${next.board.isMated},${SystemClock.elapsedRealtime()-start}")
                    out.flush()
                }
            }
            File(folder,"games.csv").bufferedWriter().use {out ->
                out.appendLine("skill,candidate_side,scenario,result,reason,plies,elapsed_ms")
                for((id,skill) in candidates) for(candidateWhite in listOf(true,false)) {
                    // The E opponent starts with a fixed, legal poor opening; afterward both
                    // sides use the unmodified engine. This is a recovery scenario, not a human model.
                    val opening=if(candidateWhite) listOf("e2e4","a7a5","d2d4","h7h5")
                        else listOf("a2a4","e7e5","h2h4","d7d5")
                    var position=ChessPosition(moves=opening)
                    engine.newGame()
                    val began=SystemClock.elapsedRealtime()
                    val name="skill${skill}-${if(candidateWhite) "white" else "black"}"
                    while(position.automaticResult()==null && !position.canClaimDraw() && position.moves.size<400) {
                        val candidateTurn=(position.board.sideToMove==Side.WHITE)==candidateWhite
                        val best=engine.search(START_FEN,position.moves,if(candidateTurn) id else 6)
                        val move=position.resolve(best)
                        if(move==null) File(folder,"failure-$name.txt").writeText("best=$best\nfen=${position.board.fen}\nhistory=${position.moves.joinToString(" ")}\nlegal=${position.legal.joinToString(" ")}\n${NativeEngine.metrics()}")
                        assertNotNull("Rejected engine response $best in $name at ${position.moves.size}",move)
                        position=position.append(move!!)
                        File(folder,"progress.txt").writeText("game=$name\nplies=${position.moves.size}\nfen=${position.board.fen}\nhistory=${position.moves.joinToString(" ")}\n")
                    }
                    val result=position.automaticResult() ?: if(position.canClaimDraw()) "1/2-1/2" else "*"
                    val reason=position.automaticResultReason() ?: if(position.canClaimDraw()) "Claimable draw claimed by harness" else "400-ply test limit"
                    out.appendLine("$skill,${if(candidateWhite) "white" else "black"},opponent_pawn_flanks,$result,$reason,${position.moves.size},${SystemClock.elapsedRealtime()-began}")
                    out.flush()
                    val san=position.san.chunked(2).mapIndexed {i,pair -> "${i+1}. ${pair.joinToString(" ")}"}.joinToString(" ")
                    File(folder,"$name.pgn").writeText("[Event \"Square Chess automated calibration\"]\n[White \"${if(candidateWhite) "Candidate $skill" else "E skill 0"}\"]\n[Black \"${if(candidateWhite) "E skill 0" else "Candidate $skill"}\"]\n[Result \"$result\"]\n\n$san $result\n")
                    File(folder,"$name.uci.txt").writeText(position.moves.joinToString(" "))
                }
            }
        } finally {engine.close()}
    }
}

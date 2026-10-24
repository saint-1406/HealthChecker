package az.healthchecker.catalog
import org.junit.Assert.*
import org.junit.Test
class ExerciseFiguresTest {
    private fun pose(t:Float,arm:Float=180f,lift:Float=0f)=FigurePose(t,t,listOf(arm,arm),listOf(arm,arm),listOf(180f,180f),listOf(180f,180f),lift)
    @Test fun standingFigureIsUprightOnTheGround() {
        val j=groundedJoints(pose(0f)).points
        assertEquals(0f,j.getValue("tn").y,1e-5f);assertEquals(0f,j.getValue("hip").x,1e-5f)
        // Head above the neck above the hips, 1.63 body units tall.
        assertTrue(j.getValue("head").y<j.getValue("neck").y&&j.getValue("neck").y<j.getValue("hip").y)
        assertEquals(1.63f,-(j.getValue("head").y-Body.HEAD),1e-4f)
        assertEquals(-0.3f,groundedJoints(pose(0f,lift=0.3f)).points.getValue("an").y,1e-5f)
    }
    @Test fun posesBlendTheShortWayRound() {
        val half=blend(pose(350f,arm=350f),pose(10f,arm=10f),0.5f)
        assertEquals(0f,((half.t%360)+360)%360,1e-4f);assertEquals(0f,((half.an[0]%360)+360)%360,1e-4f)
        assertEquals(pose(20f),blend(pose(20f),pose(80f),0f));assertEquals(80f,blend(pose(20f),pose(80f),1f).t,1e-4f)
    }
}

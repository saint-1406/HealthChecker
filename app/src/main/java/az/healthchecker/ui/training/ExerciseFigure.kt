package az.healthchecker.ui.training
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import az.healthchecker.catalog.*
import kotlin.math.floor
import kotlin.math.hypot

/** Area of all frames and props in body units, framed like the review sheet of figures.py: always room for a standing figure. */
private class FigureFrame(motion:FigureMotion) {
    val minX:Float;val maxX:Float;val minY:Float;val maxY=0.1f
    init {
        val points=motion.frames.flatMap{groundedJoints(it).points.values}
        val xs=points.map{it.x}+0f+motion.props.flatMap{if(it.type=="box")listOf(it.x0,it.x1) else listOf(it.x)}
        minX=xs.min()-0.2f;maxX=xs.max()+0.2f;minY=minOf(points.minOf{it.y}-Body.HEAD,-1.95f)-0.05f
    }
}

/** The pose a still picture shows: the working position of a two-pose movement, the middle of a longer one. */
val FigureMotion.keyFrame:FigurePose get() = frames[frames.size/2]

/**
 * Stick figure of an exercise, original to the app. Animated, it moves through the key poses and back, pausing briefly in
 * each; near limbs are solid, far ones pale. Dumbbells show as short bars in the hands.
 */
@Composable fun ExerciseFigure(motion:FigureMotion,dumbbells:Boolean,description:String?,modifier:Modifier=Modifier,animate:Boolean=true) {
    val frame=remember(motion){FigureFrame(motion)}
    // Two poses go there and back; longer sequences run round to the first pose.
    val keys=remember(motion){val n=motion.frames.size;if(n==2)listOf(0,1,0) else (0 until n).toList()+0}
    val steps=keys.size-1
    val phase=if(animate&&motion.frames.size>1)rememberInfiniteTransition("figure").animateFloat(0f,steps.toFloat(),infiniteRepeatable(tween(steps*1100,easing=LinearEasing)),"pose") else null
    val near=MaterialTheme.colorScheme.primary
    val far=near.copy(alpha=0.38f)
    val weight=MaterialTheme.colorScheme.tertiary
    val ground=MaterialTheme.colorScheme.outlineVariant
    val box=MaterialTheme.colorScheme.secondaryContainer
    val wall=MaterialTheme.colorScheme.outline
    Canvas(modifier.then(if(description!=null)Modifier.semantics{contentDescription=description} else Modifier)) {
        val pose=phase?.let{
            val p=it.value.coerceIn(0f,steps-0.0001f);val i=floor(p).toInt()
            // Each step holds the pose for a moment, then moves with ease-in-out.
            val f=FastOutSlowInEasing.transform(((p-i-0.3f)/0.7f).coerceIn(0f,1f))
            blend(motion.frames[keys[i]],motion.frames[keys[i+1]],f)
        }?:motion.keyFrame
        val w=frame.maxX-frame.minX;val h=frame.maxY-frame.minY
        val scale=minOf(size.width/w,size.height/h)
        val ox=(size.width-w*scale)/2;val oy=(size.height-h*scale)/2
        fun at(p:Pt)=Offset(ox+(p.x-frame.minX)*scale,oy+(p.y-frame.minY)*scale)
        val stroke=0.07f*scale
        drawLine(ground,at(Pt(frame.minX,0f)),at(Pt(frame.maxX,0f)),stroke*0.35f,StrokeCap.Round)
        for(prop in motion.props){
            if(prop.type=="box"){val top=at(Pt(prop.x0,-prop.h));val bottom=at(Pt(prop.x1,0f));drawRect(box,top,Size(bottom.x-top.x,bottom.y-top.y))}
            else drawLine(wall,at(Pt(prop.x,-2f)),at(Pt(prop.x,0f)),stroke*0.6f)
        }
        val j=groundedJoints(pose).points
        fun seg(a:String,b:String,color:Color){val p=j[a];val q=j[b];if(p!=null&&q!=null)drawLine(color,at(p),at(q),stroke,StrokeCap.Round)}
        fun dumbbell(side:String,color:Color){
            val e=j["e$side"]?:return;val wr=j["w$side"]?:return
            val dx=wr.x-e.x;val dy=wr.y-e.y;val len=hypot(dx,dy).takeIf{it>0}?:return
            // Across the forearm, centred in the hand.
            val nx=-dy/len*0.1f;val ny=dx/len*0.1f;val c=Pt(wr.x+dx/len*0.03f,wr.y+dy/len*0.03f)
            drawLine(color,at(Pt(c.x-nx,c.y-ny)),at(Pt(c.x+nx,c.y+ny)),stroke*1.25f,StrokeCap.Round)
        }
        for((s,c) in listOf("f" to far,"n" to near)){seg("h$s","k$s",c);seg("k$s","a$s",c);seg("a$s","t$s",c)}
        seg("hip","neck",near)
        if(pose.front){seg("sn","sf",near);seg("hn","hf",near)}
        for((s,c) in listOf("f" to far,"n" to near)){seg("s$s","e$s",c);seg("e$s","w$s",c);if(dumbbells)dumbbell(s,if(s=="f")weight.copy(alpha=0.45f) else weight)}
        j["head"]?.let{drawCircle(near,Body.HEAD*scale,at(it))}
    }
}

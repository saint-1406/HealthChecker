package az.healthchecker.catalog
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

/**
 * Key pose of an exercise scheme (tools/exercises/figures.py): absolute segment directions in degrees for a figure
 * facing right — 0 up, 90 forward, 180 down, 270 back. n are the near (or screen-left) limbs, f the far ones.
 */
@Serializable data class FigurePose(val t:Float,val h:Float,val an:List<Float>,val af:List<Float>,val ln:List<Float>,val lf:List<Float>,val lift:Float=0f,val front:Boolean=false,val dx:Float=0f)
@Serializable data class FigureProp(val type:String,val x0:Float=0f,val x1:Float=0f,val h:Float=0f,val x:Float=0f)
@Serializable data class FigureMotion(val id:String,val frames:List<FigurePose>,val props:List<FigureProp> = emptyList(),val hold:Boolean=false)

/** Segment lengths in body units; must match LEN in figures.py. */
object Body { const val TORSO=0.50f;const val NECK=0.06f;const val HEAD=0.11f;const val UPPER=0.30f;const val FORE=0.27f;const val THIGH=0.43f;const val SHIN=0.42f;const val FOOT=0.11f;const val SHOULDER_HALF=0.12f;const val HIP_HALF=0.07f }

data class Pt(val x:Float,val y:Float) { operator fun plus(o:Pt)=Pt(x+o.x,y+o.y) }
private fun vec(deg:Float,len:Float):Pt { val r=Math.toRadians(deg.toDouble());return Pt((len*sin(r)).toFloat(),(-len*cos(r)).toFloat()) }

/** Joints of a pose standing on the ground line y=0 (y grows downwards), as figures.py computes them. */
data class FigureJoints(val points:Map<String,Pt>,val front:Boolean)
fun groundedJoints(p:FigurePose):FigureJoints {
    val hip=Pt(0f,0f);val neck=vec(p.t,Body.TORSO);val head=neck+vec(p.h,Body.NECK+Body.HEAD)
    val sn:Pt;val sf:Pt;val hn:Pt;val hf:Pt
    if(p.front){sn=neck+vec(p.t-90,Body.SHOULDER_HALF);sf=neck+vec(p.t+90,Body.SHOULDER_HALF);hn=hip+vec(p.t-90,Body.HIP_HALF);hf=hip+vec(p.t+90,Body.HIP_HALF)}
    else {sn=neck;sf=neck;hn=hip;hf=hip}
    val m=mutableMapOf("hip" to hip,"neck" to neck,"head" to head,"sn" to sn,"sf" to sf,"hn" to hn,"hf" to hf)
    for((side,s,h) in listOf(Triple("n",sn,hn),Triple("f",sf,hf))){
        val arm=if(side=="n")p.an else p.af;val leg=if(side=="n")p.ln else p.lf
        val e=s+vec(arm[0],Body.UPPER);val w=e+vec(arm[1],Body.FORE);val k=h+vec(leg[0],Body.THIGH);val a=k+vec(leg[1],Body.SHIN)
        m["e$side"]=e;m["w$side"]=w;m["k$side"]=k;m["a$side"]=a
        if(!p.front)m["t$side"]=a+vec(leg[1]-90,Body.FOOT)
    }
    val low=maxOf(m.filterKeys{it!="head"}.values.maxOf{it.y},head.y+Body.HEAD)
    return FigureJoints(m.mapValues{(_,v)->Pt(v.x+p.dx,v.y-low-p.lift)},p.front)
}

/** In-between pose: every angle turns the short way round, so a 350° → 10° arm passes through 0°, not 180°. */
fun blend(a:FigurePose,b:FigurePose,f:Float):FigurePose {
    fun ang(x:Float,y:Float):Float { var d=(y-x)%360f;if(d>180f)d-=360f;if(d<-180f)d+=360f;return x+d*f }
    fun pair(x:List<Float>,y:List<Float>)=listOf(ang(x[0],y[0]),ang(x[1],y[1]))
    return FigurePose(ang(a.t,b.t),ang(a.h,b.h),pair(a.an,b.an),pair(a.af,b.af),pair(a.ln,b.ln),pair(a.lf,b.lf),a.lift+(b.lift-a.lift)*f,if(f<0.5f)a.front else b.front,a.dx+(b.dx-a.dx)*f)
}

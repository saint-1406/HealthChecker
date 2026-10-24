package az.healthchecker.ui.theme
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import az.healthchecker.domain.Season
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * What is behind every screen: the theme's background colour, the user's own picture under a veil of that colour
 * ([dim] is the veil's opacity), and during a holiday its decorations — snow and a garland for New Year, a moon, bats,
 * a web and pumpkins for Halloween. Decorations are pale and sit behind the cards, so text stays readable.
 */
@Composable fun AppBackdrop(picture:ImageBitmap?,dim:Float,season:Season?,decorations:Boolean,modifier:Modifier=Modifier) {
    val bg=MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize().background(bg)) {
        if(picture!=null){
            Image(picture,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(bg.copy(alpha=dim)))
        } else if(season!=null) {
            // A soft glow of the holiday colour at the bottom of the plain background.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent,1f to MaterialTheme.colorScheme.primary.copy(alpha=0.1f))))
        }
        if(decorations)when(season){Season.HALLOWEEN->HalloweenDecor();Season.NEW_YEAR->NewYearDecor();null->{}}
    }
}

private class Flake(val x:Float,val y:Float,val speed:Int,val radius:Float,val sway:Int,val phase:Float)

@Composable private fun NewYearDecor() {
    val dark=MaterialTheme.colorScheme.background.luminance()<0.3f
    val snow=if(dark)Color.White else Color(0xFF8FB3DA)
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val motion=rememberInfiniteTransition("new-year")
    // Whole numbers of screens per cycle keep every flake's path continuous when the cycle starts again.
    val t by motion.animateFloat(0f,1f,infiniteRepeatable(tween(18000,easing=LinearEasing)),"snow")
    val glow by motion.animateFloat(0f,1f,infiniteRepeatable(tween(2400,easing=LinearEasing)),"lights")
    val flakes=remember{List(48){i->val r=Random(i*7919+13);Flake(r.nextFloat(),r.nextFloat(),1+r.nextInt(2),1.2f+r.nextFloat()*2.6f,1+r.nextInt(2),r.nextFloat()*6.28f)}}
    val topPx=with(LocalDensity.current){top.toPx()}
    Canvas(Modifier.fillMaxSize()) {
        flakes.forEach{f->
            val y=((f.y+t*f.speed)%1f)*size.height
            val x=(f.x+0.02f*sin(2*PI.toFloat()*t*f.sway+f.phase))*size.width
            drawCircle(snow.copy(alpha=if(dark)0.55f+0.2f*(f.radius/4f) else 0.75f),f.radius.dp.toPx(),Offset(x,y))
        }
        // A garland of lights hanging just under the status bar, above the screen titles.
        val bulbs=12;val left=8.dp.toPx();val span=size.width-2*left;val sag=9.dp.toPx();val y0=topPx-6.dp.toPx()
        fun at(f:Float)=Offset(left+span*f,y0+sag*4*f*(1-f))
        val wire=Path().apply{moveTo(at(0f).x,at(0f).y);for(i in 1..40)at(i/40f).let{lineTo(it.x,it.y)}}
        drawPath(wire,(if(dark)Color.White else Color(0xFF2E4A3D)).copy(alpha=0.35f),style=Stroke(1.2.dp.toPx()))
        val colors=listOf(Color(0xFFE53935),Color(0xFFFFC107),Color(0xFF43A047),Color(0xFF1E88E5))
        for(i in 0 until bulbs){
            val p=at((i+0.5f)/bulbs);val on=0.45f+0.55f*(0.5f+0.5f*sin(2*PI.toFloat()*(glow+i*0.37f)))
            drawCircle(colors[i%colors.size].copy(alpha=0.25f*on),7.dp.toPx(),p.copy(y=p.y+5.dp.toPx()))
            drawCircle(colors[i%colors.size].copy(alpha=0.6f+0.4f*on),3.2.dp.toPx(),p.copy(y=p.y+5.dp.toPx()))
        }
    }
}

private class Bat(val y:Float,val speed:Int,val size:Float,val start:Float,val flap:Float)

@Composable private fun HalloweenDecor() {
    val dark=MaterialTheme.colorScheme.background.luminance()<0.3f
    val ink=if(dark)MaterialTheme.colorScheme.tertiary.copy(alpha=0.32f) else Color(0xFF3B2A4A).copy(alpha=0.28f)
    val moon=if(dark)Color(0xFFFFE6A8) else Color(0xFFFFB74D)
    val pumpkin=Color(0xFFF57C00)
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val motion=rememberInfiniteTransition("halloween")
    val t by motion.animateFloat(0f,1f,infiniteRepeatable(tween(32000,easing=LinearEasing)),"bats")
    val wing by motion.animateFloat(0f,1f,infiniteRepeatable(tween(520,easing=FastOutSlowInEasing),RepeatMode.Reverse),"wings")
    val bats=remember{List(5){i->val r=Random(i*104729+7);Bat(0.1f+r.nextFloat()*0.22f,1+r.nextInt(2),14f+r.nextFloat()*12f,r.nextFloat(),r.nextFloat())}}
    val topPx=with(LocalDensity.current){top.toPx()}
    Canvas(Modifier.fillMaxSize()) {
        // Moon with a soft halo in the free space of the header, a lantern pumpkin below it.
        val c=Offset(size.width*0.64f,topPx+44.dp.toPx())
        drawCircle(Brush.radialGradient(listOf(moon.copy(alpha=0.22f),Color.Transparent),c,70.dp.toPx()),70.dp.toPx(),c)
        drawCircle(moon.copy(alpha=if(dark)0.42f else 0.3f),24.dp.toPx(),c)
        jackOLantern(Offset(c.x+46.dp.toPx(),c.y+40.dp.toPx()),13.dp.toPx(),pumpkin.copy(alpha=if(dark)0.6f else 0.5f),faceColor(dark))
        web(Offset(0f,topPx),74.dp.toPx(),ink)
        bats.forEach{b->
            // Off one edge and in at the other; whole speeds keep the flight continuous between cycles.
            val x=(((b.start+t*b.speed)%1f)*1.2f-0.1f)*size.width
            val y=b.y*size.height+10.dp.toPx()*sin(2*PI.toFloat()*(t*b.speed*3+b.flap))
            val phase=(wing+b.flap)%1f
            drawPath(batPath(Offset(x,y),b.size.dp.toPx(),0.35f+0.65f*phase),ink)
        }
    }
}
private fun faceColor(dark:Boolean)=if(dark)Color(0xFF1A0F05) else Color(0xFF5A2A00)

/** A bat seen from the front; [open] lifts the wings (0 down, 1 up). */
private fun batPath(c:Offset,s:Float,open:Float):Path = Path().apply {
    val lift=s*(0.55f*open-0.15f)
    moveTo(c.x,c.y-0.18f*s)
    for(side in listOf(-1f,1f)){
        val k=side
        lineTo(c.x+k*0.22f*s,c.y-0.2f*s-lift*0.4f)
        quadraticTo(c.x+k*0.62f*s,c.y-0.5f*s-lift,c.x+k*1.0f*s,c.y-0.15f*s-lift)
        quadraticTo(c.x+k*0.82f*s,c.y+0.02f*s,c.x+k*0.66f*s,c.y+0.06f*s)
        quadraticTo(c.x+k*0.52f*s,c.y-0.04f*s,c.x+k*0.4f*s,c.y+0.12f*s)
        quadraticTo(c.x+k*0.26f*s,c.y+0.02f*s,c.x+k*0.12f*s,c.y+0.24f*s)
        lineTo(c.x,c.y+0.32f*s)
        if(side<0)moveTo(c.x,c.y-0.18f*s)
    }
    close()
    // Head with ears.
    addOval(androidx.compose.ui.geometry.Rect(c.x-0.13f*s,c.y-0.3f*s,c.x+0.13f*s,c.y-0.04f*s))
    moveTo(c.x-0.11f*s,c.y-0.26f*s);lineTo(c.x-0.08f*s,c.y-0.42f*s);lineTo(c.x-0.02f*s,c.y-0.28f*s);close()
    moveTo(c.x+0.11f*s,c.y-0.26f*s);lineTo(c.x+0.08f*s,c.y-0.42f*s);lineTo(c.x+0.02f*s,c.y-0.28f*s);close()
}

/** A corner spider web: radial threads and sagging rings. */
private fun DrawScope.web(corner:Offset,r:Float,color:Color) {
    val threads=5;val stroke=1.dp.toPx()
    val angles=(0..threads).map{i->(PI/2*i/threads).toFloat()}
    angles.forEach{a->drawLine(color,corner,corner+Offset(cos(a)*r,sin(a)*r),stroke)}
    for(ring in 1..4){
        val rr=r*ring/4.3f
        for(i in 0 until threads){
            val a0=angles[i];val a1=angles[i+1]
            val p0=corner+Offset(cos(a0)*rr,sin(a0)*rr);val p1=corner+Offset(cos(a1)*rr,sin(a1)*rr)
            val mid=corner+Offset(cos((a0+a1)/2)*rr*0.86f,sin((a0+a1)/2)*rr*0.86f)
            drawPath(Path().apply{moveTo(p0.x,p0.y);quadraticTo(mid.x,mid.y,p1.x,p1.y)},color,style=Stroke(stroke))
        }
    }
}

/** A pumpkin of three lobes with a stem and a carved face. */
private fun DrawScope.jackOLantern(c:Offset,r:Float,color:Color,face:Color) {
    drawOval(color,Offset(c.x-r*1.05f,c.y-r*0.82f),Size(r*1.2f,r*1.64f))
    drawOval(color,Offset(c.x-r*0.15f,c.y-r*0.82f),Size(r*1.2f,r*1.64f))
    drawOval(color.copy(alpha=(color.alpha+0.08f).coerceAtMost(1f)),Offset(c.x-r*0.6f,c.y-r*0.86f),Size(r*1.2f,r*1.72f))
    drawRoundRect(Color(0xFF558B2F).copy(alpha=color.alpha),Offset(c.x-r*0.1f,c.y-r*1.12f),Size(r*0.2f,r*0.34f),androidx.compose.ui.geometry.CornerRadius(r*0.06f))
    val f=face.copy(alpha=color.alpha*0.9f)
    fun tri(a:Offset,b:Offset,d:Offset)=drawPath(Path().apply{moveTo(a.x,a.y);lineTo(b.x,b.y);lineTo(d.x,d.y);close()},f)
    tri(Offset(c.x-r*0.45f,c.y-r*0.05f),Offset(c.x-r*0.15f,c.y-r*0.05f),Offset(c.x-r*0.3f,c.y-r*0.35f))
    tri(Offset(c.x+r*0.15f,c.y-r*0.05f),Offset(c.x+r*0.45f,c.y-r*0.05f),Offset(c.x+r*0.3f,c.y-r*0.35f))
    drawPath(Path().apply{moveTo(c.x-r*0.5f,c.y+r*0.2f);quadraticTo(c.x,c.y+r*0.62f,c.x+r*0.5f,c.y+r*0.2f);quadraticTo(c.x,c.y+r*0.4f,c.x-r*0.5f,c.y+r*0.2f);close()},f)
}

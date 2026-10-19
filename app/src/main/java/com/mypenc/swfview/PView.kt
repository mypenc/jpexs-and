package com.mypenc.swfview

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class PView(ctx:Context):View(ctx){
    var item:Item?=null
    var tick=0
    var play=true
    var zoom=1f
    var px=0f
    var py=0f
    var onTick:((Int)->Unit)?=null
    var lx=0f
    var ly=0f
    val chk=Paint()
    val sg=ScaleGestureDetector(ctx,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(d:ScaleGestureDetector):Boolean{
            val z=Math.max(0.1f,Math.min(40f,zoom*d.scaleFactor))
            val k=z/zoom
            val cx=d.focusX-width/2f
            val cy=d.focusY-height/2f
            px=cx-(cx-px)*k
            py=cy-(cy-py)*k
            zoom=z
            invalidate()
            return true
        }
    })

    init {
        val b=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        val c=Canvas(b)
        c.drawColor(0xffeeeeee.toInt())
        val p=Paint()
        p.color=0xffd4d4d4.toInt()
        c.drawRect(0f,0f,16f,16f,p)
        c.drawRect(16f,16f,32f,32f,p)
        chk.shader=BitmapShader(b,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT)
    }
    fun fps():Long{
        val f=Store.swf?.frameRate ?: 24f
        return (1000/Math.max(1f,Math.min(60f,f))).toLong()
    }
    fun reset(){
        zoom=1f
        px=0f
        py=0f
        invalidate()
    }
    fun setPlaying(p:Boolean){
        play=p
        invalidate()
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        sg.onTouchEvent(e)
        if(!sg.isInProgress){
            if(e.actionMasked==MotionEvent.ACTION_DOWN||e.actionMasked==MotionEvent.ACTION_POINTER_UP){
                val i=if(e.actionMasked==MotionEvent.ACTION_POINTER_UP) (if(e.actionIndex==0) 1 else 0) else 0
                lx=e.getX(i)
                ly=e.getY(i)
            }else if(e.actionMasked==MotionEvent.ACTION_MOVE&&e.pointerCount==1){
                px+=e.x-lx
                py+=e.y-ly
                lx=e.x
                ly=e.y
                invalidate()
            }
        }
        return true
    }
    override fun onDraw(c:Canvas){
        c.drawPaint(chk)
        val it=item ?: return
        val r=Store.rnd ?: return
        val b=r.bounds(it)
        val k=Math.min(width/Math.max(b.width(),1f),height/Math.max(b.height(),1f))*0.9f
        c.save()
        c.translate(width/2f+px,height/2f+py)
        c.scale(k*zoom,k*zoom)
        c.translate(-b.centerX(),-b.centerY())
        try{ r.render(c,it,tick) }catch(e:Throwable){}
        c.restore()
        val n=it.frames()
        if(play&&n>1){
            tick=(tick+1)%n
            onTick?.invoke(tick)
            postInvalidateDelayed(fps())
        }
    }
}

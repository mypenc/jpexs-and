package com.mypenc.swfview

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class PreviewActivity:Activity(){
    override fun onCreate(b:Bundle?){
        super.onCreate(b)
        val it=Store.cur
        if(it==null){
            finish()
            return
        }
        val d=resources.displayMetrics.density
        val root=LinearLayout(this)
        root.orientation=LinearLayout.VERTICAL
        val ttl=TextView(this)
        ttl.text="${Item.names[it.kind]} ${it.name}"
        ttl.textSize=16f
        ttl.setPadding((12*d).toInt(),(12*d).toInt(),(12*d).toInt(),(8*d).toInt())
        root.addView(ttl)
        val pv=PView(this)
        pv.item=it
        root.addView(pv,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        val bar=LinearLayout(this)
        bar.gravity=Gravity.CENTER_VERTICAL
        bar.setPadding((8*d).toInt(),(4*d).toInt(),(8*d).toInt(),(8*d).toInt())
        val n=it.frames()
        val pb=Button(this)
        pb.text="Pause"
        val rb=Button(this)
        rb.text="Fit"
        val sk=SeekBar(this)
        sk.max=Math.max(0,n-1)
        val lb=TextView(this)
        lb.text="1/$n"
        if(n>1){
            bar.addView(pb)
            bar.addView(sk,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
            bar.addView(lb)
        }
        bar.addView(rb)
        root.addView(bar)
        pv.onTick={ t ->
            sk.progress=t
            lb.text="${t+1}/$n"
        }
        pb.setOnClickListener{
            pv.setPlaying(!pv.play)
            pb.text=if(pv.play) "Pause" else "Play"
        }
        rb.setOnClickListener{ pv.reset() }
        sk.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s:SeekBar,p:Int,u:Boolean){
                if(!u) return
                pv.tick=p
                lb.text="${p+1}/$n"
                pv.invalidate()
            }
            override fun onStartTrackingTouch(s:SeekBar){
                pv.play=false
                pb.text="Play"
            }
            override fun onStopTrackingTouch(s:SeekBar){}
        })
        setContentView(root)
    }
}

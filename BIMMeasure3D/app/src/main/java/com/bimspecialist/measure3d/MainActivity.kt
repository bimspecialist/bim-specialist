package com.bimspecialist.measure3d

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimspecialist.measure3d.core.*
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import java.util.Locale
import kotlin.math.*

private enum class Mode { LENGTH, AREA, SCAN }
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MeasureApp() }
    }
}
@Composable
private fun MeasureApp() {
    val ar = remember { Locale.getDefault().language == "ar" }
    val context = LocalContext.current
    fun label(a: String, en: String) = if (ar) a else en
    var mode by remember { mutableStateOf(Mode.LENGTH) }
    val anchors = remember { mutableStateListOf<Anchor>() }
    val sampler = remember { PointSampler() }
    var scanning by remember { mutableStateOf(false) }
    var scanCount by remember { mutableIntStateOf(0) }
    var frames by remember { mutableIntStateOf(0) }
    val latestFrame = remember { arrayOfNulls<com.google.ar.core.Frame>(1) }
    var preview by remember { mutableStateOf<List<Vec3>?>(null) }
    var message by remember { mutableStateOf(label("حرّك الهاتف لتحديد الأسطح، ثم انقر على نقاط القياس.", "Scan a surface, then tap points to measure.")) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    val createDocument=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes=pendingBytes
        if(uri!=null&&bytes!=null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use{it.write(bytes)} ?: error("Output unavailable")
                message=label("حُفظ الملف بنجاح", "File saved")
            }catch(e:Exception){message=label("فشل الحفظ: ","Save failed: ")+(e.localizedMessage ?: "")}
        }
        pendingBytes=null
    }
    fun save(name:String,data:ByteArray){pendingBytes=data;createDocument.launch(name)}
    fun clear(){ anchors.forEach { it.detach() };anchors.clear() }
    fun changeMode(next:Mode){
        if(next==mode)return
        scanning=false;preview=null;clear();sampler.clear();scanCount=0;mode=next
        message=when(next){
            Mode.SCAN->label("مسح نقطي تجريبي، ليس نموذج طباعة ثلاثية الأبعاد.", "Experimental point cloud, not a printable mesh.")
            else->label("اختر نقاطًا على سطح مكتشف.", "Tap points on a detected plane.")
        }
    }
    DisposableEffect(Unit){ onDispose{anchors.forEach{it.detach()}} }
    val vertices=anchors.map{a->val p=a.pose.translation;Vec3(p[0].toDouble(),p[1].toDouble(),p[2].toDouble())}
    val result=try{
        when(mode){
            Mode.LENGTH->if(vertices.size==2) label("الطول","Length")+": "+String.format(Locale.US,"%.3f m",Geometry.length(vertices[0],vertices[1])) else "${vertices.size}/2"
            Mode.AREA->if(vertices.size>=3) label("المساحة","Area")+": "+String.format(Locale.US,"%.3f m²",Geometry.planarArea(vertices)) else "${vertices.size}/3+"
            Mode.SCAN->label("عدد النقاط: ","Points: ")+scanCount
        }
    }catch(e:IllegalArgumentException){label("خطأ القياس: ","Invalid measure: ")+(e.message ?: "")}
    MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF4EA6FF))){
        Box(Modifier.fillMaxSize().background(Color(0xFF0D1524))){
            if(mode==Mode.SCAN && preview!=null){
                CloudPreview(preview.orEmpty(),Modifier.fillMaxSize())
            }else{
                ARSceneView(
                    modifier=Modifier.fillMaxSize(),
                    depthMode=Config.DepthMode.DISABLED,
                    planeRenderer=mode!=Mode.SCAN,
                    onSessionFailed={e->message=label("خطأ في الواقع المعزز: ","AR error: ")+(e.localizedMessage ?: "")},
                    onSessionUpdated={_,frame->
                        latestFrame[0] = frame
                        if(mode==Mode.SCAN&&scanning&&frame.camera.trackingState==TrackingState.TRACKING){
                            frames++
                            if(frames%12==0)try{
                                val t=frame.camera.pose.translation
                                val camera=Vec3(t[0].toDouble(),t[1].toDouble(),t[2].toDouble())
                                frame.acquirePointCloud().use{cloud->
                                    val data=cloud.points.duplicate()
                                    while(data.remaining()>=4){
                                        val p=Vec3(data.get().toDouble(),data.get().toDouble(),data.get().toDouble())
                                        val confidence=data.get()
                                        if(p.distanceTo(camera)<=5.0)sampler.add(p,confidence)
                                    }
                                }
                                scanCount=sampler.count()
                            }catch(_:Exception){}
                        }
                    },
                    onTouchEvent={event,_->
                        if(event.actionMasked==MotionEvent.ACTION_UP&&mode!=Mode.SCAN){
                            val hit = latestFrame[0]?.hitTest(event)?.firstOrNull { it.trackable is Plane && (it.trackable as Plane).isPoseInPolygon(it.hitPose) }
                            if(hit==null){
                                message=label("لم يُكتشف سطح مناسب","No detected surface")
                            }else if((mode==Mode.LENGTH && anchors.size<2)||(mode==Mode.AREA && anchors.size<24)){
                                try{
                                    anchors.add(hit.createAnchor())
                                    message=label("أضيفت النقطة","Point added")
                                }catch(e:Exception){message=e.localizedMessage ?: "AR error"}
                            }
                        }
                        mode!=Mode.SCAN && event.actionMasked==MotionEvent.ACTION_UP
                    }
                )
            }
            Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.SpaceBetween){
                Column(Modifier.fillMaxWidth().background(Color(0xEC0D1524)).padding(12.dp)){
                    Text("BIM Measure 3D",fontWeight=FontWeight.Bold,fontSize=22.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                        ModeButton(label("الطول","Length"),mode==Mode.LENGTH,Modifier.weight(1f)){changeMode(Mode.LENGTH)}
                        ModeButton(label("المساحة","Area"),mode==Mode.AREA,Modifier.weight(1f)){changeMode(Mode.AREA)}
                        ModeButton(label("مسح 3D","3D Scan"),mode==Mode.SCAN,Modifier.weight(1f)){changeMode(Mode.SCAN)}
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(result,color=Color(0xFF9DD1FF),fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                }
                Column(Modifier.fillMaxWidth().background(Color(0xEE0D1524)).padding(12.dp)){
                    Text(message,fontSize=12.sp,maxLines=3)
                    Spacer(Modifier.height(8.dp))
                    when(mode){
                        Mode.LENGTH->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Button(onClick={clear()},modifier=Modifier.weight(1f)){Text(label("مسح","Clear"))}
                            Button(onClick={save("length_mm.dxf",Exports.dxf(vertices,false))},enabled=vertices.size==2,modifier=Modifier.weight(1f)){Text("DXF")}
                        }
                        Mode.AREA->Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            Button(onClick={if(anchors.isNotEmpty())anchors.removeAt(anchors.lastIndex).detach()},enabled=anchors.isNotEmpty(),modifier=Modifier.weight(1f)){Text(label("تراجع","Undo"))}
                            Button(onClick={clear()},modifier=Modifier.weight(1f)){Text(label("مسح","Clear"))}
                            Button(onClick={
                                try{Geometry.planarArea(vertices);save("area_mm.dxf",Exports.dxf(vertices,true))}
                                catch(e:Exception){message=e.message ?: "Invalid area"}
                            },enabled=vertices.size>=3,modifier=Modifier.weight(1f)){Text("DXF")}
                        }
                        Mode.SCAN->{
                            Text(label("PLY يحتوي نقاطًا متفرقة فقط ولا يصلح للطباعة مباشرة.","PLY contains sparse points, not a printable mesh."),fontSize=11.sp,color=Color(0xFFF6C36A))
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                Button(onClick={
                                    if(scanning){scanning=false;scanCount=sampler.count()}
                                    else{sampler.clear();scanCount=0;frames=0;preview=null;scanning=true}
                                },modifier=Modifier.weight(1f)){Text(if(scanning)label("إيقاف","Stop")else label("ابدأ","Start"))}
                                Button(onClick={preview=if(preview==null)sampler.snapshot()else null},enabled=!scanning&&sampler.count()>0,modifier=Modifier.weight(1f)){
                                    Text(if(preview==null)label("معاينة","Preview")else label("كاميرا","Camera"))
                                }
                                Button(onClick={save("scan_points_m.ply",Exports.ply(sampler.snapshot()))},enabled=!scanning&&sampler.count()>0,modifier=Modifier.weight(1f)){Text("PLY")}
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun ModeButton(text:String,selected:Boolean,modifier:Modifier,onClick:()->Unit){
    if(selected) Button(onClick=onClick,modifier=modifier,contentPadding=PaddingValues(horizontal=2.dp)){Text(text,maxLines=1,fontSize=11.sp)}
    else OutlinedButton(onClick=onClick,modifier=modifier,contentPadding=PaddingValues(horizontal=2.dp)){Text(text,maxLines=1,fontSize=11.sp)}
}
@Composable
private fun CloudPreview(points:List<Vec3>,modifier:Modifier=Modifier){
    var yaw by remember { mutableFloatStateOf(0.5f) }
    var pitch by remember { mutableFloatStateOf(0.3f) }
    val sample=remember(points){if(points.size<=3500)points else points.filterIndexed{i,_->i%max(1,points.size/3500)==0}.take(3500)}
    val center=remember(sample){if(sample.isEmpty())Vec3(0.0,0.0,0.0)else Vec3(sample.map{it.x}.average(),sample.map{it.y}.average(),sample.map{it.z}.average())}
    val span=remember(sample,center){max(0.1,sample.maxOfOrNull{it.distanceTo(center)} ?: 1.0)}
    Canvas(modifier.background(Color(0xFF111D2E)).pointerInput(Unit){
        detectDragGestures{change,drag->change.consume();yaw+=drag.x*0.008f;pitch=(pitch+drag.y*0.008f).coerceIn(-1.4f,1.4f)}
    }){
        val scale=minOf(size.width,size.height)*0.38/span.toFloat()
        val cy=cos(yaw.toDouble());val sy=sin(yaw.toDouble());val cp=cos(pitch.toDouble());val sp=sin(pitch.toDouble())
        for(p in sample){
            val v=p-center;val x=v.x*cy-v.z*sy;val z=v.x*sy+v.z*cy;val y=v.y*cp-z*sp
            drawCircle(Color(0xFF4EA6FF),radius=1.5.dp.toPx(),center=Offset(size.width/2+(x*scale).toFloat(),size.height/2-(y*scale).toFloat()))
        }
    }
}
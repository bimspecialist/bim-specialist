package com.bimspecialist.measure3d.core
import java.util.Locale
object Exports {
    private fun num(v:Double) = String.format(Locale.US,"%.6f",v)
    fun ply(points:List<Vec3>):ByteArray {
        val output=StringBuilder("ply\nformat ascii 1.0\ncomment unit meter; local ARCore coordinates\n")
        output.append("element vertex ${points.size}\nproperty float x\nproperty float y\nproperty float z\nend_header\n")
        points.forEach { p -> output.append("${num(p.x)} ${num(p.y)} ${num(p.z)}\n") }
        return output.toString().toByteArray(Charsets.US_ASCII)
    }
    fun dxf(points:List<Vec3>,closed:Boolean):ByteArray {
        require(points.size>=2)
        val output=StringBuilder("0\nSECTION\n2\nHEADER\n9\n\u0024ACADVER\n1\nAC1015\n9\n\u0024INSUNITS\n70\n4\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n")
        for(i in 0 until (if(closed) points.size else points.size-1)){
            val a=points[i];val b=points[(i+1)%points.size]
            output.append("0\nLINE\n8\nBIM_MEASURE\n")
            output.append("10\n${num(a.x*1000)}\n20\n${num(a.y*1000)}\n30\n${num(a.z*1000)}\n")
            output.append("11\n${num(b.x*1000)}\n21\n${num(b.y*1000)}\n31\n${num(b.z*1000)}\n")
        }
        output.append("0\nENDSEC\n0\nEOF\n")
        return output.toString().toByteArray(Charsets.US_ASCII)
    }
}

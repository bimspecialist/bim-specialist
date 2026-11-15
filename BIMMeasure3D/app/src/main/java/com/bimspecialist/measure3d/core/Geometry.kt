package com.bimspecialist.measure3d.core

import kotlin.math.abs
import kotlin.math.sqrt

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun minus(other: Vec3) = Vec3(x-other.x,y-other.y,z-other.z)
    operator fun plus(other: Vec3) = Vec3(x+other.x,y+other.y,z+other.z)
    fun dot(p: Vec3) = x*p.x+y*p.y+z*p.z
    fun cross(p: Vec3) = Vec3(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x)
    fun norm() = sqrt(dot(this))
    fun distanceTo(p: Vec3) = (this-p).norm()
}
object Geometry {
    fun length(a: Vec3, b: Vec3) = a.distanceTo(b)
    fun planarArea(points: List<Vec3>, tolerance: Double = 0.04): Double {
        require(points.size in 3..64) { "3 to 64 points required" }
        require(points.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
        require(points.indices.none { i -> points[i].distanceTo(points[(i+1)%points.size]) < 0.001 }) { "Overlapping points" }
        val origin = points[0]
        val vectors = points.map { it-origin }
        val normal = (1 until points.size-1).map { vectors[it].cross(vectors[it+1]) }
            .firstOrNull { it.norm() > 1e-8 } ?: error("Collinear points")
        val n = Vec3(normal.x/normal.norm(),normal.y/normal.norm(),normal.z/normal.norm())
        require(vectors.all { abs(it.dot(n)) <= tolerance }) { "Nonplanar polygon" }
        val p = points.map { q -> when {
            abs(n.x)>=abs(n.y) && abs(n.x)>=abs(n.z) -> Pair(q.y,q.z)
            abs(n.y)>=abs(n.z) -> Pair(q.x,q.z)
            else -> Pair(q.x,q.y)
        }}
        fun orient(a: Pair<Double,Double>,b: Pair<Double,Double>,c: Pair<Double,Double>) =
            (b.first-a.first)*(c.second-a.second)-(b.second-a.second)*(c.first-a.first)
        fun on(a: Pair<Double,Double>,b: Pair<Double,Double>,c: Pair<Double,Double>) =
            b.first >= minOf(a.first,c.first)-1e-9 && b.first <= maxOf(a.first,c.first)+1e-9 &&
            b.second >= minOf(a.second,c.second)-1e-9 && b.second <= maxOf(a.second,c.second)+1e-9
        fun intersects(a: Pair<Double,Double>,b: Pair<Double,Double>,c: Pair<Double,Double>,d: Pair<Double,Double>):Boolean {
            val x=orient(a,b,c);val y=orient(a,b,d);val z=orient(c,d,a);val w=orient(c,d,b)
            return (x*y < -1e-9 && z*w < -1e-9) ||
                (abs(x)<1e-9&&on(a,c,b)) || (abs(y)<1e-9&&on(a,d,b)) ||
                (abs(z)<1e-9&&on(c,a,d)) || (abs(w)<1e-9&&on(c,b,d))
        }
        for(i in p.indices) for(j in i+1 until p.size){
            if(j==i+1 || (i==0 && j==p.lastIndex))continue
            require(!intersects(p[i],p[(i+1)%p.size],p[j],p[(j+1)%p.size])){"Self-intersecting polygon"}
        }
        var sum=Vec3(0.0,0.0,0.0)
        for(i in 1 until vectors.size-1) sum+=vectors[i].cross(vectors[i+1])
        val result=abs(sum.dot(n))*0.5
        require(result>1e-8) { "Zero area" }
        return result
    }
}

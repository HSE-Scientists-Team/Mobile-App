package org.matchlab.academic_net

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
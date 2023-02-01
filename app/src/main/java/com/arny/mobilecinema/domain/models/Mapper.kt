package com.arny.mobilecinema.domain.models

interface Mapper<SRC, DST> {
    fun transform(data: SRC): DST
}

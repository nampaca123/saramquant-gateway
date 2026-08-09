package me.saramquantgateway.domain.store

data class PageResult<T>(val content: List<T>, val totalElements: Long, val hasNext: Boolean)

package me.saramquantgateway.infra.duckdb

// 레이크 테이블명을 DuckDB FROM 절에 넣을 수 있는 스캔 표현식으로 바꾼다.
fun interface LakeTableResolver {
    fun ref(table: String): String

    fun invalidateAll() {}
}

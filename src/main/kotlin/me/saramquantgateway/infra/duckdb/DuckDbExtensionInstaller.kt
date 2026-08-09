package me.saramquantgateway.infra.duckdb

import java.sql.DriverManager

// 이미지 빌드 단계에서 확장을 extension_directory에 미리 설치하고 LOAD까지 검증한다.
fun main() {
    val directory = System.getenv("DUCKDB_EXT_DIR")
        ?: throw IllegalStateException("DUCKDB_EXT_DIR is required to bake DuckDB extensions")

    DriverManager.getConnection("jdbc:duckdb:").use { connection ->
        connection.createStatement().use { statement ->
            statement.execute("SET extension_directory='${directory.replace('\\', '/')}'")
            DuckDbConfig.EXTENSIONS.forEach { statement.execute("INSTALL $it") }
            DuckDbConfig.EXTENSIONS.forEach { statement.execute("LOAD $it") }
        }
    }
    println("DuckDB extensions ${DuckDbConfig.EXTENSIONS} installed into $directory")
}

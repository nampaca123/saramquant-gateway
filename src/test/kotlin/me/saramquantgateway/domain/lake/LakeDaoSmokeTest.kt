package me.saramquantgateway.domain.lake

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.saramquantgateway.domain.enum.market.Benchmark
import me.saramquantgateway.domain.enum.market.Country
import me.saramquantgateway.domain.enum.market.Maturity
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.infra.duckdb.DuckDbQueryExecutor
import me.saramquantgateway.infra.duckdb.GlueLakeTableResolver
import me.saramquantgateway.infra.duckdb.LakeTableResolver
import org.duckdb.DuckDBConnection
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.glue.GlueClient
import java.sql.DriverManager
import java.time.LocalDate

// 실제 Iceberg 테이블 대상 스모크 — 데스크톱 기본 프로파일 대신 프로젝트 IAM 키만 사용한다.
@EnabledIfEnvironmentVariable(named = "SARAMQUANT_IAM_KEY_ACCESS", matches = ".+")
class LakeDaoSmokeTest {

    @Test
    fun `stocks dao reads active korean stocks from the real lake`() {
        val page = StockLakeDao(executor, resolver).findPageByMarket(Market.KR_KOSPI, null, 0, 5)

        assumeTrue(page.totalElements > 0, "stocks table is not seeded yet")
        assertTrue(page.content.isNotEmpty())
        assertTrue(page.content.all { it.market == Market.KR_KOSPI && it.isActive })
    }

    @Test
    fun `daily prices dao reads the KR market partition from the real lake`() {
        val stock = StockLakeDao(executor, resolver).findPageByMarket(Market.KR_KOSPI, null, 0, 1).content.firstOrNull()
        assumeTrue(stock != null, "stocks table is not seeded yet")

        val prices = PriceLakeDao(executor, resolver)
            .findByStockIdAndDateBetween(stock!!.id, Market.KR_KOSPI, LocalDate.now().minusDays(400), LocalDate.now())

        assumeTrue(prices.isNotEmpty(), "daily_prices has no recent KR rows yet")
        assertTrue(prices.all { it.stockId == stock.id })
    }

    @Test
    fun `risk badge dao reads the snapshot table from the real lake`() {
        val counts = RiskBadgeLakeDao(executor, resolver, jacksonObjectMapper()).countByMarketAndTier()

        assumeTrue(counts.isNotEmpty(), "risk_badges snapshot is not seeded yet")
        assertTrue(counts.sumOf { it.count } > 0)
    }

    // 데이터 유무와 무관하게, DAO가 매핑하는 모든 컬럼이 실제 Iceberg 스키마에 존재하는지 확인한다.
    @Test
    fun `every dao query resolves against the real iceberg schemas`() {
        val stockDao = StockLakeDao(executor, resolver)
        val priceDao = PriceLakeDao(executor, resolver)
        val fundamentalDao = FundamentalLakeDao(executor, resolver)

        stockDao.findByIds(listOf(1L))
        stockDao.findDistinctSectors(Market.KR_KOSPI)
        priceDao.findTop2PerStock(listOf(1L), "KR")
        priceDao.findTop2Benchmark(Benchmark.KR_KOSPI)
        IndicatorLakeDao(executor, resolver).findLatestByStockIds(listOf(1L))
        fundamentalDao.findLatestByStockIds(listOf(1L))
        fundamentalDao.findFinancialsByStockId(1L, Market.KR_KOSPI)
        FactorLakeDao(executor, resolver).findLatestCovariance(Market.KR_KOSPI)
        SectorLakeDao(executor, resolver).findLatestByMarket(Market.KR_KOSPI)
        MarketRefLakeDao(executor, resolver).findLatestRiskFreeRate(Country.KR, Maturity.Y1)
        MarketRefLakeDao(executor, resolver).findExchangeRateOnOrBefore("USD/KRW", LocalDate.now())
    }

    companion object {
        private const val REGION = "ap-northeast-2"
        private const val DATABASE = "saramquant"

        private lateinit var connection: DuckDBConnection
        private lateinit var executor: DuckDbQueryExecutor
        private lateinit var glue: GlueClient
        private lateinit var resolver: LakeTableResolver

        @BeforeAll
        @JvmStatic
        fun setUp() {
            val accessKey = System.getenv("SARAMQUANT_IAM_KEY_ACCESS")
            val secretKey = System.getenv("SARAMQUANT_IAM_KEY_SECRET")
            connection = DriverManager.getConnection("jdbc:duckdb:") as DuckDBConnection
            connection.createStatement().use { statement ->
                listOf(
                    "INSTALL httpfs", "INSTALL iceberg", "LOAD httpfs", "LOAD iceberg",
                    "CREATE OR REPLACE SECRET s3sec (TYPE s3, KEY_ID '$accessKey', SECRET '$secretKey', REGION '$REGION')",
                ).forEach { statement.execute(it) }
            }
            executor = DuckDbQueryExecutor(connection)
            glue = GlueClient.builder()
                .region(Region.of(REGION))
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)),
                )
                .build()
            resolver = GlueLakeTableResolver(glue, DATABASE)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            executor.close()
            glue.close()
        }
    }
}

package me.saramquantgateway.domain.repository.market

import me.saramquantgateway.domain.entity.market.SectorAggregate
import me.saramquantgateway.domain.entity.market.SectorAggregateId
import me.saramquantgateway.domain.enum.stock.Market
import org.springframework.data.jpa.repository.JpaRepository

interface SectorAggregateRepository : JpaRepository<SectorAggregate, SectorAggregateId> {

    fun findTop1ByMarketAndSectorOrderByDateDesc(market: Market, sector: String): SectorAggregate?

    fun findTop1ByMarketOrderByDateDesc(market: Market): SectorAggregate?

    fun findByMarketAndDate(market: Market, date: java.time.LocalDate): List<SectorAggregate>
}

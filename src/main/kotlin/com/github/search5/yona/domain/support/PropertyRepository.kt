package com.github.search5.yona.domain.support

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PropertyRepository : JpaRepository<Property, Long> {
    fun findByName(name: PropertyName): Property?
}

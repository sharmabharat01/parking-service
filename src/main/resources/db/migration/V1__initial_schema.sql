CREATE EXTENSION IF NOT EXISTS postgis;


-- ============================================================
-- Static car park information
--
-- Source coordinates are provided in SVY21 (EPSG:3414).
-- They are transformed to WGS84 (EPSG:4326) during ingestion.
-- ============================================================

CREATE TABLE car_parks (
                           carpark_number VARCHAR(50) PRIMARY KEY,

                           address TEXT NOT NULL,

    -- WGS84 geographic point used for proximity searches.
                           location GEOGRAPHY(POINT, 4326) NOT NULL,

                           car_park_type VARCHAR(100),
                           parking_system VARCHAR(100),
                           short_term_parking VARCHAR(100),
                           free_parking VARCHAR(100),
                           night_parking VARCHAR(100),

                           carpark_decks INTEGER,
                           gantry_height DOUBLE PRECISION,
                           carpark_basement VARCHAR(20),

                           created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- Spatial index for nearby car park searches.
CREATE INDEX idx_car_parks_location
    ON car_parks
    USING GIST (location);


-- ============================================================
-- Latest availability for each car park and lot type
--
-- Lot types:
-- C = Cars
-- H = Heavy vehicles
-- S = Motorcycles with side car
-- Y = Motorcycles
--
-- Java representation:
-- LotType.C
-- LotType.H
-- LotType.S
-- LotType.Y
-- ============================================================

CREATE TABLE carpark_availability (
                                      carpark_number VARCHAR(50) NOT NULL,

                                      lot_type VARCHAR(1) NOT NULL,

                                      total_lots INTEGER NOT NULL,
                                      available_lots INTEGER NOT NULL,

    -- Timestamp provided by the Singapore API.
                                      source_updated_at TIMESTAMPTZ NOT NULL,

    -- Timestamp when our service ingested the record.
                                      ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                      PRIMARY KEY (carpark_number, lot_type),

                                      CONSTRAINT fk_availability_carpark
                                          FOREIGN KEY (carpark_number)
                                              REFERENCES car_parks(carpark_number),

                                      CONSTRAINT chk_lot_type
                                          CHECK (lot_type IN ('C', 'H', 'S', 'Y')),

                                      CONSTRAINT chk_total_lots_non_negative
                                          CHECK (total_lots >= 0),

                                      CONSTRAINT chk_available_lots_non_negative
                                          CHECK (available_lots >= 0),

                                      CONSTRAINT chk_available_lots_not_exceed_total
                                          CHECK (available_lots <= total_lots)
);


-- Supports filtering availability by lot type and availability.
CREATE INDEX idx_availability_lot_type_available
    ON carpark_availability (lot_type, available_lots);


-- Supports freshness checks during search/reconciliation.
CREATE INDEX idx_availability_source_updated
    ON carpark_availability (source_updated_at);


-- ============================================================
-- Ingestion / synchronization runs
--
-- Keeps operational information about each sync attempt.
-- ============================================================

CREATE TABLE sync_runs (
                           id BIGSERIAL PRIMARY KEY,

                           sync_type VARCHAR(50) NOT NULL,
                           status VARCHAR(30) NOT NULL,

                           started_at TIMESTAMPTZ NOT NULL,
                           completed_at TIMESTAMPTZ,

                           records_received INTEGER NOT NULL DEFAULT 0,
                           records_updated INTEGER NOT NULL DEFAULT 0,
                           records_rejected INTEGER NOT NULL DEFAULT 0,

                           error_message TEXT
);


CREATE INDEX idx_sync_runs_type_started
    ON sync_runs (sync_type, started_at DESC);
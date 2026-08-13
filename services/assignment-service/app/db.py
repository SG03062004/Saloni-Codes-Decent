from __future__ import annotations

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.models import DriverCandidate, Settings

_engine = None
_session_factory = None


def init_db(settings: Settings) -> None:
    global _engine, _session_factory
    _engine = create_async_engine(settings.db_url, pool_pre_ping=True)
    _session_factory = async_sessionmaker(_engine, expire_on_commit=False)


def get_session_factory():
    return _session_factory


async def upsert_driver_location(
    session: AsyncSession,
    driver_id: str,
    lat: float,
    lng: float,
    is_available: bool,
) -> None:
    await session.execute(
        text(
            """
            INSERT INTO driver_locations (driver_id, location, is_available)
            VALUES (
                :driver_id,
                ST_GeomFromText(
                    CONCAT('POINT(', :lng, ' ', :lat, ')'),
                    4326,
                    'axis-order=long-lat'
                ),
                :is_available
            )
            ON DUPLICATE KEY UPDATE
                location = ST_GeomFromText(
                    CONCAT('POINT(', :lng, ' ', :lat, ')'),
                    4326,
                    'axis-order=long-lat'
                ),
                is_available = :is_available,
                updated_at = CURRENT_TIMESTAMP
            """
        ),
        {
            "driver_id": driver_id,
            "lat": lat,
            "lng": lng,
            "is_available": int(is_available),
        },
    )
    await session.commit()


async def find_nearest_drivers(
    session: AsyncSession,
    order_lat: float,
    order_lng: float,
    limit: int = 5,
) -> list[DriverCandidate]:

    rows = await session.execute(
        text(
            """
            SELECT
                driver_id,
                ST_Latitude(location) AS lat,
                ST_Longitude(location) AS lng,
                ST_Distance_Sphere(
                    location,
                    ST_GeomFromText(
                        CONCAT('POINT(', :order_lng, ' ', :order_lat, ')'),
                        4326,
                        'axis-order=long-lat'
                    )
                ) AS distance_m
            FROM driver_locations
            WHERE is_available = 1
            ORDER BY distance_m ASC
            LIMIT :lim
            """
        ),
        {
            "order_lat": order_lat,
            "order_lng": order_lng,
            "lim": limit,
        },
    )

    return [
        DriverCandidate(
            driver_id=r.driver_id,
            lat=r.lat,
            lng=r.lng,
            distance_m=r.distance_m,
        )
        for r in rows.mappings()
    ]

from __future__ import annotations
from typing import Protocol
from app.logging_config import get_logger

log = get_logger("notification-service.channel")


class NotificationChannel(Protocol):
    async def send(self, recipient: str, subject: str, body: dict) -> None: ...

    @property
    def name(self) -> str: ...


class MockChannel:
    """Logs the notification payload as structured JSON. Swap for real SMTP/SMS later."""

    @property
    def name(self) -> str:
        return "mock"

    async def send(self, recipient: str, subject: str, body: dict) -> None:
        log.info(
            "notification_dispatched",
            extra={
                "channel": self.name,
                "recipient": recipient,
                "subject": subject,
                "body": body,
            },
        )

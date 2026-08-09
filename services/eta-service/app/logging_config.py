import contextvars
import logging

from pythonjsonlogger import jsonlogger

_order_id_var: contextvars.ContextVar[str | None] = contextvars.ContextVar(
    "orderId", default=None
)


def set_order_id(order_id: str | None) -> None:
    _order_id_var.set(order_id)


class _OrderIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        oid = _order_id_var.get()
        if oid:
            record.orderId = oid
        return True


def get_logger(name: str) -> logging.Logger:
    logger = logging.getLogger(name)
    if not logger.handlers:
        handler = logging.StreamHandler()
        formatter = jsonlogger.JsonFormatter(
            fmt="%(asctime)s %(levelname)s %(name)s %(message)s",
            rename_fields={"asctime": "timestamp", "levelname": "level"},
        )
        handler.setFormatter(formatter)
        handler.addFilter(_OrderIdFilter())
        logger.addHandler(handler)
        logger.setLevel(logging.INFO)
        logger.propagate = False
    return logger

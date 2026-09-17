import re
import unicodedata

_NON_ALNUM = re.compile(r"[^0-9a-z\u1100-\u11ff\u3130-\u318f\uac00-\ud7a3]+")


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).lower()
    return _NON_ALNUM.sub("", value)


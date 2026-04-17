from __future__ import annotations

import sys

from launcher import main


if __name__ == "__main__":
    sys.argv = [sys.argv[0], "analyze", *sys.argv[1:]]
    raise SystemExit(main())

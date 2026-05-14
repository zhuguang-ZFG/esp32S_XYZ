"""M0c.2 fake_device_server 单元测试。"""
import json
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent))
from fake_device_server.app import build_arg_parser


class TestArgParser(unittest.TestCase):
    def test_defaults(self):
        args = build_arg_parser().parse_args([])
        self.assertEqual(args.host, "127.0.0.1")
        self.assertEqual(args.port, 8100)
        self.assertEqual(args.fake_u1_host, "127.0.0.1")
        self.assertEqual(args.fake_u1_port, 7799)
        self.assertEqual(args.business_base_url, "")

    def test_custom_port(self):
        args = build_arg_parser().parse_args(["--port", "9200"])
        self.assertEqual(args.port, 9200)

    def test_business_url(self):
        args = build_arg_parser().parse_args(["--business-base-url", "http://localhost:8080"])
        self.assertEqual(args.business_base_url, "http://localhost:8080")


if __name__ == "__main__":
    unittest.main()

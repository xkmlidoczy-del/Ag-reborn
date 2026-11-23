import unittest
from types import SimpleNamespace
from unittest.mock import patch

from local_java_scenario import fits


class LocalAdmissionTest(unittest.TestCase):
    def setUp(self):
        self.args = SimpleNamespace(jvms=1, max_jvms=3, cores=1, memory_mib=384)
        self.job = {10: dict(jvms=1, limit=3, cores=1, mib=384)}
        # The first Java process is a grandchild of the active supervisor.
        self.usage = ({12: 11, 11: 10, 10: 1}, {12: 200}, 1.2)

    @patch("local_java_scenario.os.cpu_count", return_value=12)
    def test_admission(self, _):
        self.assertTrue(fits(self.job, self.args, self.usage, (True, 6000)))
        self.assertFalse(fits(self.job, self.args, self.usage, (False, 6000)))
        self.assertFalse(fits(self.job, self.args, self.usage, (True, 3500)))
        self.assertFalse(fits(self.job, self.args, (*self.usage[:2], 7.5), (True, 6000)))
        self.assertFalse(fits(self.job, self.args, self.usage, (True, None)))
        self.assertTrue(fits({}, self.args, ({}, {}, 0.0), (True, None)))

    def test_timing_window_waits_for_an_external_jvm(self):
        self.args.quiet_java = True
        self.assertFalse(fits({}, self.args, ({90: 1}, {90: 96}, 0), (True, 9000)))
        self.assertTrue(fits({}, self.args, ({}, {}, 0), (True, 9000)))

    def test_capacity_includes_unrelated_and_pending_jdks(self):
        jobs = {10: dict(jvms=2, limit=3, cores=2, mib=768)}
        with_external = ({12: 11, 11: 10}, {12: 200, 90: 100}, 1.0)
        self.assertFalse(fits(jobs, self.args, with_external, (True, 9000)))
        self.args.max_jvms = 5
        self.assertFalse(fits(jobs, self.args, with_external, (True, 9000)))


if __name__ == "__main__":
    unittest.main()

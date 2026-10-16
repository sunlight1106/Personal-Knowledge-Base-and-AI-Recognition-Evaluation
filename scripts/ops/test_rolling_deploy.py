import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import rolling_deploy as deployment

class DeploymentTest(unittest.TestCase):
    def test_candidate_failure_keeps_current_route_and_cleans_lock(self):
        with tempfile.TemporaryDirectory() as directory:
            state=Path(directory)/'state.json';overlay=Path(directory)/'slots.json'
            with patch.object(deployment,'ROOT',Path(directory)),patch.object(deployment,'STATE',state),patch.object(deployment,'OVERLAY',overlay),patch.object(deployment,'run',return_value=b'sha256:fixture'),patch.object(deployment,'compose'),patch.object(deployment,'healthy',return_value=False),patch.object(deployment,'switch') as switch:
                with self.assertRaises(RuntimeError):deployment.deploy('fixture:local',True)
                switch.assert_not_called();self.assertFalse(state.exists());self.assertFalse(state.with_suffix('.lock').exists())

    def test_switch_failure_rolls_route_back_without_claiming_success(self):
        with tempfile.TemporaryDirectory() as directory:
            state=Path(directory)/'state.json';overlay=Path(directory)/'slots.json'
            with patch.object(deployment,'ROOT',Path(directory)),patch.object(deployment,'STATE',state),patch.object(deployment,'OVERLAY',overlay),patch.object(deployment,'run',return_value=b'sha256:fixture'),patch.object(deployment,'compose'),patch.object(deployment,'healthy',side_effect=[True,False]),patch.object(deployment,'switch') as switch:
                with self.assertRaises(RuntimeError):deployment.deploy('fixture:local',True)
                self.assertEqual([c.args[0] for c in switch.call_args_list],['backend-blue','backend']);self.assertFalse(state.exists())

    def test_success_then_rollback_preserves_previous_slot(self):
        with tempfile.TemporaryDirectory() as directory:
            state=Path(directory)/'state.json';overlay=Path(directory)/'slots.json'
            with patch.object(deployment,'ROOT',Path(directory)),patch.object(deployment,'STATE',state),patch.object(deployment,'OVERLAY',overlay),patch.object(deployment,'run',return_value=b'sha256:fixture'),patch.object(deployment,'compose'),patch.object(deployment,'healthy',return_value=True),patch.object(deployment,'switch'):
                deployment.deploy('fixture:local',True);self.assertEqual(json.loads(state.read_text())['active'],'backend-blue')
                deployment.deploy(None,False,True);self.assertEqual(json.loads(state.read_text())['active'],'backend')
                with self.assertRaises(ValueError):deployment.deploy('fixture:local',False)

if __name__=='__main__':unittest.main()

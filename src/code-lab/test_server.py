import http.client
import json
import threading
import unittest
from unittest.mock import patch
import server


class ServiceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        server.TOKEN='synthetic-code-lab-test-token-only-32'
        cls.http=server.ThreadingHTTPServer(('127.0.0.1',0),server.Handler)
        cls.thread=threading.Thread(target=cls.http.serve_forever,daemon=True);cls.thread.start()
    @classmethod
    def tearDownClass(cls):cls.http.shutdown();cls.http.server_close();cls.thread.join()
    def post(self,payload,headers=None):
        conn=http.client.HTTPConnection('127.0.0.1',self.http.server_port,timeout=10)
        conn.request('POST','/execute',body=payload,headers=headers or {})
        response=conn.getresponse();data=json.loads(response.read());status=response.status;conn.close();return status,data
    def test_authentication_precedes_execution(self):
        with patch.object(server,'run_process') as runner:
            self.assertEqual(self.post(b'{}')[0],401)
            self.assertEqual(self.post(b'{}',{'Authorization':'Bearer wrong'})[0],401)
            runner.assert_not_called()
    def test_real_disposable_process_and_request_limit(self):
        headers={'Authorization':'Bearer '+server.TOKEN}
        status,result=self.post(json.dumps({'source':'print(2+3)','language':'python'}).encode(),headers)
        self.assertEqual(status,200);self.assertEqual(result['output'],'5\n')
        self.assertEqual(self.post(b'x'*65537,headers)[0],413)
        self.assertEqual(self.post(b'not json',headers)[1]['status'],'error')
    def test_saturated_service_does_not_queue_unbounded_jobs(self):
        server.SLOTS.acquire();server.SLOTS.acquire()
        try:self.assertEqual(self.post(b'{}',{'Authorization':'Bearer '+server.TOKEN})[0],429)
        finally:server.SLOTS.release();server.SLOTS.release()
    def test_infinite_loop_is_bounded_and_does_not_poison_next_run(self):
        result=server.run_process(json.dumps({'source':'while True:\n    pass','language':'python'}).encode())
        self.assertEqual(result['status'],'limited')
        self.assertEqual(server.run_process(b'{"source":"print(1)","language":"python"}')['output'],'1\n')

if __name__=='__main__':unittest.main()

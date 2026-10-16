"""Configure private, persistent Prometheus monitoring; never print its credential."""
from pathlib import Path
import json
import secrets
import os

def configure(root):
    env_path=root/'.env';text=env_path.read_text(encoding='utf-8-sig')
    token=next((line.split('=',1)[1].strip() for line in text.splitlines() if line.startswith('MONITORING_TOKEN=')), '')
    if len(token)<32:
        token=secrets.token_hex(32)
        text='\n'.join(line for line in text.splitlines() if not line.startswith('MONITORING_TOKEN='))+'\nMONITORING_TOKEN='+token+'\n'
        env_path.write_text(text,encoding='utf-8')
    directory=root/'.runtime/monitoring';directory.mkdir(parents=True,exist_ok=True)
    os.chmod(directory,0o700)
    config={'global':{'scrape_interval':'15s','evaluation_interval':'15s'},'rule_files':['/etc/prometheus/alerts.yml'],'scrape_configs':[{'job_name':'platform','metrics_path':'/actuator/prometheus','http_headers':{'X-Metrics-Token':{'values':[token]}},'static_configs':[{'targets':['frontend:80','model-worker:8081']}]}]}
    (directory/'prometheus.yml').write_text(json.dumps(config),encoding='utf-8')
    # The private parent directory protects the token on the host; the container
    # runs as an unprivileged user and must be able to read this bind-mounted file.
    os.chmod(directory/'prometheus.yml',0o644)
    print('Monitoring configured. Start using compose.monitoring.yaml after recreating application services to load the token.')

if __name__=='__main__':configure(Path(__file__).resolve().parents[2])

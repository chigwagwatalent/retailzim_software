#!/usr/bin/env python3
"""Build an isolated existing-system fuel update without unrelated working-tree changes."""
import argparse, io, json, os, shutil, subprocess, tarfile, tempfile
from pathlib import Path

root=Path(__file__).resolve().parents[3]
p=argparse.ArgumentParser();p.add_argument('--maven',required=True);p.add_argument('--agent');args=p.parse_args()
commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
work=Path(tempfile.mkdtemp(prefix='retailzim-fuel-release-'))
archive=subprocess.check_output(['git','archive','HEAD','backend/pom.xml','backend/src'],cwd=root)
with tarfile.open(fileobj=io.BytesIO(archive)) as t:
 for member in t.getmembers():
  if member.isfile():
   target=work/member.name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(t.extractfile(member).read())
backend=work/'backend'
overlays=['pom.xml','src/main/java/com/retailzw/service/FuelStationService.java','src/main/java/com/retailzw/controller/api/FuelStationApiController.java','src/main/java/com/retailzw/controller/web/ShopWebController.java','src/main/java/com/retailzw/config/DataInitializer.java','src/test/java/com/retailzw/web/DatabaseWorkflowIntegrationTest.java','src/test/java/com/retailzw/web/FuelUpgradeIntegrationTest.java']
for name in overlays:shutil.copy2(root/'backend'/name,backend/name)
pom=backend/'pom.xml';s=pom.read_text().replace('<version>1.0.0</version>','<version>1.0.1-fuel-java21</version>',1);pom.write_text(s)
# Retain the deployed security/navigation behaviour. Inject secrets externally rather than shipping live values.
import re
for name in ['application.yml','application-prod.yml']:
 path=backend/'src/main/resources'/name;s=path.read_text()
 s=re.sub(r'(?m)^    username: (root|\$\{DB_USERNAME:[^}]+\})$', '    username: ${DB_USERNAME}',s)
 s=re.sub(r'(?m)^    password: (?!\$\{MAIL).+$','    password: ${DB_PASSWORD}',s,count=1)
 s=re.sub(r'\$\{MAIL_PASSWORD:[^}]+\}', '${MAIL_PASSWORD:}',s)
 s=re.sub(r'(?m)^  secret: .+$','  secret: ${JWT_SECRET}',s)
 s=re.sub(r'(?m)^  api-key: .+$','  api-key: ${SMILEPAY_API_KEY:}',s)
 s=re.sub(r'(?m)^  api-secret: .+$','  api-secret: ${SMILEPAY_API_SECRET:}',s)
 if name=='application-prod.yml':
  s=re.sub(r'(?m)^    url: jdbc:mysql:.+$','    url: ${DB_URL}',s)
  s=s.replace('ddl-auto: update','ddl-auto: none').replace('baseline-on-migrate: true','baseline-on-migrate: false')
  s=s.replace('  port: 8080','  port: ${PORT:8080}').replace('  address: 0.0.0.0','  address: ${SERVER_ADDRESS:127.0.0.1}')
  s=s.replace('base-url: https://admin.retailzw.co.zw','base-url: ${APP_BASE_URL:https://admin.retailzw.co.zw}')
 path.write_text(s)
# Existing-system fuel update ends at V55. No account disabling or fresh-install repair migrations.
assert not list((backend/'src/main/resources/db/migration').glob('V5[67]__*'))
state={'work':str(work),'backend':str(backend),'baseline_commit':commit,'overlays':overlays,'version':'1.0.1-fuel-java21'}
Path('/tmp/retailzim-fuel-release-state.json').write_text(json.dumps(state,indent=2))
cmd=[args.maven,'-o','verify']
if args.agent:cmd.append('-DargLine=-javaagent:'+args.agent)
with open('/tmp/retailzim-fuel-release-build.log','w') as f:r=subprocess.run(cmd,cwd=backend,stdout=f,stderr=subprocess.STDOUT)
print(json.dumps({**state,'build_exit':r.returncode}));raise SystemExit(r.returncode)

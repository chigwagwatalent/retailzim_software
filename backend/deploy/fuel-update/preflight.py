#!/usr/bin/env python3
"""Read-only live upgrade check. Never repairs, baselines or migrates a database."""
import os, subprocess, tempfile, zipfile, sys
from pathlib import Path
bundle=Path(__file__).resolve().parent
for key in ['DB_URL','DB_USERNAME','DB_PASSWORD']:
 if not os.environ.get(key):sys.exit('Set '+key+' in the environment; do not pass credentials as command arguments.')
jar=bundle/'retailzw-fuel-java21.jar'
if not jar.exists():sys.exit('Candidate JAR is missing next to preflight.py')
source='''
import java.sql.*;
import org.flywaydb.core.Flyway;
public class FuelUpgradePreflight {
 public static void main(String[] args) throws Exception {
  String url=System.getenv("DB_URL"),user=System.getenv("DB_USERNAME"),pass=System.getenv("DB_PASSWORD");
  try(var c=DriverManager.getConnection(url,user,pass)) {
   c.setReadOnly(true);
   try(var s=c.createStatement()) {
    var r=s.executeQuery("select max(cast(version as unsigned)) from flyway_schema_history where success=1");r.next();int version=r.getInt(1);
    if(version<51||version>55)throw new IllegalStateException("Supported existing-system baseline: V51 through V55; found V"+version);
    r=s.executeQuery("select count(*) from flyway_schema_history where success=0");r.next();if(r.getInt(1)>0)throw new IllegalStateException("A failed migration exists. Investigate before updating.");
    System.out.println("Existing schema version: V"+version);
    r=s.executeQuery("select count(*) from saas_admins where is_active=1");r.next();if(r.getInt(1)<1)throw new IllegalStateException("No active platform administrator.");
    r=s.executeQuery("select count(*) from information_schema.columns where table_schema=database() and ((table_name='branches' and column_name='module_type') or (table_name='tenants' and column_name='business_mode') or (table_name='users' and column_name='role_id'))");r.next();if(r.getInt(1)!=3)throw new IllegalStateException("Existing core schema is missing required module/access fields.");
    if(version<52){r=s.executeQuery("select count(*) from information_schema.tables where table_schema=database() and left(table_name,5)='fuel_'");r.next();if(r.getInt(1)>0)throw new IllegalStateException("Fuel tables exist before V52. Review partial/manual changes before upgrading.");}
   }
  }
  var flyway=Flyway.configure().dataSource(url,user,pass).locations("filesystem:"+args[0]).baselineOnMigrate(false).cleanDisabled(true).ignoreMigrationPatterns("*:pending").load();
  var validation=flyway.validateWithResult();
  if(!validation.validationSuccessful){for(var invalid:validation.invalidMigrations)System.err.println(invalid.version+": "+invalid.errorDetails.errorMessage);throw new IllegalStateException("Migration history does not match this candidate. Do not repair checksums automatically.");}
  for(var pending:flyway.info().pending()) {
   int version=Integer.parseInt(pending.getVersion().getVersion());
   if(version<52||version>55)throw new IllegalStateException("Unexpected non-fuel migration V"+version);
   System.out.println("Pending fuel migration: V"+version+" "+pending.getDescription());
  }
  System.out.println("PREFLIGHT PASSED: candidate/history compatible. No database changes made.");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='retailzim-fuel-preflight-') as temp:
 work=Path(temp);libs=work/'lib';libs.mkdir();migrations=work/'migration';migrations.mkdir()
 with zipfile.ZipFile(jar) as z:
  for name in z.namelist():
   if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):(libs/Path(name).name).write_bytes(z.read(name))
   if name.startswith('BOOT-INF/classes/db/migration/') and name.endswith('.sql'):(migrations/Path(name).name).write_bytes(z.read(name))
 path=work/'FuelUpgradePreflight.java';path.write_text(source)
 java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
 result=subprocess.run([java,'--class-path',str(libs/'*'),str(path),str(migrations)])
 sys.exit(result.returncode)

# Retail Zim Front Website Database

The community, engagement, and visit tracking features use MySQL.

## Local Setup

Run the schema with a MySQL admin user:

```powershell
$env:MYSQL_PWD='your-admin-password'
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -u root --default-character-set=utf8mb4 -e "source C:/path/to/frontwebsite/database/mysql-schema.sql"
Remove-Item Env:\MYSQL_PWD
```

The schema creates:

- `connecte_retail_comunity.site_visits`
- `connecte_retail_comunity.community_posts`
- `connecte_retail_comunity.community_engagements`
- `connecte_retail_comunity.community_answers`

## Production Configuration

Set these environment variables on the PHP host:

```text
RETAILZIM_DB_HOST=localhost
RETAILZIM_DB_PORT=3306
RETAILZIM_DB_NAME=connecte_retail_comunity
RETAILZIM_DB_USER=connecte_retail_comunity
RETAILZIM_DB_PASS=@cHigwagwa1t@
RETAILZIM_DB_CHARSET=utf8mb4
RETAILZIM_ADMIN_API_KEY=change-this-shared-secret
```

For production, use the dedicated MySQL user with privileges only on the `connecte_retail_comunity` database. Set `RETAILZIM_ADMIN_API_KEY` to the same value as the Spring SaaS admin `RETAILZIM_FRONT_SITE_API_KEY` so only the admin system can publish official answers.

## JSON Endpoints

- `GET https://retailzw.co.zw/api/community/posts`
- `POST https://retailzw.co.zw/api/community/answer`
- `GET https://retailzw.co.zw/api/visits/stats`

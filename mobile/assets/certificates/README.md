# Windows HTTPS trust

Public, self-signed ISRG Root X1 and X2 certificates downloaded from the
official Let's Encrypt certificate repository on 2026-09-10:

- https://letsencrypt.org/certs/isrgrootx1.pem
- https://letsencrypt.org/certs/isrg-root-x2.pem
- Chain documentation: https://letsencrypt.org/certificates/

Only these public roots supplement the Windows app's default trust context.
No certificate validation bypass, leaf pinning, or machine-wide trust-store
modification is used. Review upstream trust policy when updating the release.

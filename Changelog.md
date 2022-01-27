## KmpSqlencrypt Change Log

**0.4.1**

- changed maven artifact name to remove "Sqlcipher" in name, now using "kmp-sqlencrypt". 
- Apple podspec name also changed, Framework name is still "KmpSqlencrypt.framework"
- maven publish moved to kotlin multiplatform setup
- Ionspin v 0.3.4 (was 0.3.3)
- updated Readme.md
- added javadoc to artifacts
- Gradle 7.3.3 (was 7.3.2)

**0.4.0**

- Initial published release. Built using:
    - SqlCipher 4.5.0 
    - Sqlite 3.36.0
    - OpenSSL 3.0.1
    - Note that Sqlcipher is using some HMAC APIs deprecated with OpenSSL 3.x resulting in warnings during builds. Next release of SqlCipher is supposed to fix this. 

## KmpSqlencrypt Change Log

**0.4.2**
- 0.4.1 inadvertently made **SqlcipherDatabase var invalidPassword** a suspend function. Removed suspend.
- Readme changes
- added helper function for enabling foreign keys pragma 
- added missing specRepo and a workaround for issue KT-42105 to cocoapods so podspec file points to github
- 0.4.1 breaks database open on encrypted DBs due to new encoding check being done too soon, moved it.
- upgrading to Ionspin 0.3.4 has what turns out to be a breaking change in its narrowing functions on float and double. Fixed.
- added unit test for basic functionality when using UTF-16LE encoding

**0.4.1** Warning this tag has two breaking changes. Not caught because unit tests didn't run. See 0.4.2.

- changed maven artifact name to remove "Sqlcipher" in name, now using "kmp-sqlencrypt". 
- Apple podspec name also changed, Framework name is still "KmpSqlencrypt.framework"
- maven publish moved to kotlin multiplatform setup
- maven publish includes Dokka artifact  
- Ionspin v 0.3.4 (was 0.3.3)
- updated Readme.md
- added javadoc to artifacts
- Gradle 7.3.3 (was 7.3.2)
- added path, invalidPassword lambda, and onOpenPragmas lambda to [sqlcipher] configuration. Added open function overload to rely on the values from [sqlcipher] configuration.
- added encoding pragma to open process

**0.4.0**

- Initial published release. Built using:
    - SqlCipher 4.5.0 
    - Sqlite 3.36.0
    - OpenSSL 3.0.1
    - Note that Sqlcipher is using some HMAC APIs deprecated with OpenSSL 3.x resulting in warnings during builds. Next release of SqlCipher is supposed to fix this. 

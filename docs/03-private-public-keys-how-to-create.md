# How to Create Private and Public Keys for JWT Signing and Validation

This document explains multiple ways to create the private/public key material used by the BFF and downstream services.

## 1. What you need in this project

The current design needs:

- a **private key** for the BFF to sign JWTs
- a **public certificate/public key** for downstream services to verify JWTs

In the current code:

- BFF reads a PEM private key string from configuration
- users-service reads a PEM public key string from configuration

The keypair may still be generated from keystore/certificate tooling, but the running apps now consume string values.

## 2. Recommended concepts first

### 2.1 Keystore

A keystore is a container file that can store:

- private keys
- certificates
- passwords/aliases metadata

In Java projects, common formats are:

- `PKCS12` (`.p12`, `.pfx`)
- `JKS`

For modern projects, `PKCS12` is generally preferred.

### 2.2 Certificate

A certificate usually contains:

- the public key
- identity metadata like CN/O/OU
- a signature proving certificate integrity

For this JWT use case, the main thing we need from the certificate is the public key.

### 2.3 PEM vs DER

Common file encodings:

- PEM = text file with `-----BEGIN ...-----`
- DER = binary form

PEM is easier to inspect and share in source-controlled dev setups.

## 3. Method 1: Java `keytool` with PKCS12 keystore

This is the method already used in the project.

### 3.1 Generate a private key and self-signed certificate

Run from the workspace root:

```powershell
keytool -genkeypair \
  -alias bff-jwt-key \
  -keyalg RSA \
  -keysize 2048 \
  -sigalg SHA256withRSA \
  -storetype PKCS12 \
  -keystore src/main/resources/keys/bff-jwt-keystore.p12 \
  -storepass changeit \
  -keypass changeit \
  -dname "CN=BFF JWT, OU=Dev, O=Example, L=Local, ST=NA, C=US" \
  -validity 3650
```

What this creates:

- RSA keypair
- private key stored in PKCS12 keystore
- self-signed certificate stored with the key

### 3.2 Export the public certificate

```powershell
keytool -exportcert -rfc \
  -alias bff-jwt-key \
  -keystore src/main/resources/keys/bff-jwt-keystore.p12 \
  -storepass changeit \
  -file users-service/src/main/resources/keys/bff-jwt-public-cert.pem
```

Why `-rfc` matters:

- it exports PEM text form instead of binary DER

### 3.3 What to configure after that

BFF config:

- keystore location
- keystore password
- key alias
- key password

Users-service config:

- public cert location
- expected issuer
- expected audience

### 3.4 Pros

- native for Java
- easy for Spring Boot apps
- stores private key safely in one file
- simple local development setup

### 3.5 Cons

- command syntax is verbose
- certificate management is less friendly than some OpenSSL workflows

## 4. Method 2: OpenSSL private key + certificate

OpenSSL is common outside Java tooling.

### 4.1 Generate RSA private key

```bash
openssl genrsa -out bff-jwt-private.pem 2048
```

### 4.2 Generate self-signed certificate from that key

```bash
openssl req -new -x509 \
  -key bff-jwt-private.pem \
  -out bff-jwt-public-cert.pem \
  -days 3650 \
  -subj "/CN=BFF JWT/OU=Dev/O=Example/L=Local/ST=NA/C=US"
```

Now you have:

- `bff-jwt-private.pem`
- `bff-jwt-public-cert.pem`

### 4.3 Convert private key and certificate into PKCS12 for Java BFF

If the BFF wants PKCS12 instead of raw PEM:

```bash
openssl pkcs12 -export \
  -out bff-jwt-keystore.p12 \
  -inkey bff-jwt-private.pem \
  -in bff-jwt-public-cert.pem \
  -name bff-jwt-key
```

Then move files to:

- `src/main/resources/keys/bff-jwt-keystore.p12`
- `users-service/src/main/resources/keys/bff-jwt-public-cert.pem`

### 4.4 Pros

- very common tooling
- flexible
- easy when working across different languages/platforms

### 4.5 Cons

- extra conversion step for Java keystore-based setup
- more moving pieces if your app expects PKCS12 directly

## 5. Method 3: OpenSSL public key export without certificate

You can also export a plain public key instead of a certificate.

### 5.1 Generate private key

```bash
openssl genrsa -out bff-jwt-private.pem 2048
```

### 5.2 Export public key

```bash
openssl rsa -in bff-jwt-private.pem -pubout -out bff-jwt-public.pem
```

This gives:

- private key PEM
- public key PEM

This is valid for JWT use too.

However, in the current project the users-service code is loading an X.509 certificate, not a raw public key PEM.

So if you choose this route, you would also need to change the code to load a raw public key file.

## 6. Method 4: Generate inside Java code

You can also generate an RSA keypair programmatically.

Example conceptually:

```java
KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
generator.initialize(2048);
KeyPair keyPair = generator.generateKeyPair();
```

Then you can:

- save the private key
- save the public key
- build a keystore
- build a certificate with additional libraries if needed

This approach is useful when:

- building internal tooling
- automating test fixtures
- generating temporary keys for integration tests

### 6.1 Pros

- fully automatable
- useful in test setups
- no external CLI required once implemented

### 6.2 Cons

- more code
- harder than `keytool` for simple manual setup
- creating certificates is more involved than just generating a keypair

## 7. Method 5: PowerShell / Windows certificate store route

On Windows, you can also generate certificates with PowerShell or certificate tools and export them.

Typical high-level flow:

- create a self-signed certificate in Windows cert store
- export public certificate as `.cer` or PEM
- export private key as PFX/PKCS12

This method can work well in Windows-heavy environments.

Example direction using PowerShell cmdlets:

- `New-SelfSignedCertificate`
- `Export-PfxCertificate`
- `Export-Certificate`

Then convert or place outputs where your apps expect them.

### 7.1 Pros

- convenient on Windows
- integrates with cert store tools

### 7.2 Cons

- more Windows-specific
- PEM conversion may still be needed depending on output format

## 8. Which method should you choose?

### Best for this current project

Use any method that gives you a valid RSA private key and matching RSA public key in PEM form, because the running apps now expect string-based PEM values.

### Best for cross-platform mixed environments

Use **Method 2 (OpenSSL + PKCS12 export)**.

### Best for automated tests

Use **Method 4 (programmatic generation)**.

## 9. How the generated files map into this project

### BFF side

The BFF signs JWTs using the PEM private key string configured in:

- `src/main/resources/application.yml`

Used by:

- `src/main/java/com/example/bff/service/InternalJwtService.java`

### Users-service side

The users service verifies JWTs using the PEM public key string configured in:

- `users-service/src/main/resources/application.yml`

Used by:

- `users-service/src/main/java/com/example/userservice/util/JwtSecurityUtils.java`

## 10. Key rotation guidance

Sooner or later keys change.

A basic rotation process is:

1. generate a new keypair
2. deploy new public certificate to downstream services
3. switch BFF to sign with new private key
4. optionally keep old public key available temporarily if multiple keys are supported
5. retire old keypair

Current project uses a single keypair model, so rotation is manual.

## 11. Safety guidance

### 11.1 Do not share private key broadly

Private key should remain only in the signing service.

### 11.2 Public key/certificate can be distributed

Downstream services need only public verification material.

### 11.3 Do not hardcode real production passwords

Values like `changeit` are fine only for local development and demos.

### 11.4 Prefer secure secret storage in real environments

For production, use something like:

- environment variables
- secret manager
- vault
- Kubernetes secret
- cloud key management systems

## 12. Quick verification checks

After generating keys, verify these:

### 12.1 Keystore contains the alias

```powershell
keytool -list -keystore src/main/resources/keys/bff-jwt-keystore.p12 -storepass changeit
```

### 12.2 Public key or certificate can be inspected

```powershell
keytool -printcert -file users-service/src/main/resources/keys/bff-jwt-public-cert.pem
```

### 12.3 Public key matches the BFF private key

If they do not match, downstream JWT validation will fail even if everything else looks correct.

## 13. Practical recommendation in one sentence

For this project, generate a valid RSA keypair by any method you prefer, then place the PEM private key string in the BFF config and the matching PEM public key string in the users-service config.

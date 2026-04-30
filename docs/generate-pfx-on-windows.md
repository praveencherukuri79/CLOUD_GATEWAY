# Generate a PFX file on Windows

This project uses a PKCS#12 keystore (`.pfx`) for internal JWT signing.

Current app settings are configured in [src/main/resources/application.yml](../src/main/resources/application.yml):

- `app.internal-jwt.keystore-location`
- `app.internal-jwt.keystore-password`
- `app.internal-jwt.key-alias`

## Prerequisites

Install a JDK on the Windows laptop.

The `keytool` utility comes with the JDK.

To verify it is available, open PowerShell and run:

```powershell
keytool -help
```

## Values used by this project

Example values used in this repository:

- Keystore file: `src/main/resources/keys/bff-jwt-keystore.pfx`
- Alias: `bff-internal-jwt`
- Store type: `PKCS12`

You can keep your own password, but it must match the value configured for:

- `APP_INTERNAL_JWT_KEYSTORE_PASSWORD`

## Where the password is set

The password is set when the `.pfx` file is created.

In the `keytool` command, this part sets the password:

```powershell
-storepass changeit
```

In this project, the same password is also used for the private key entry:

```powershell
-keypass changeit
```

So if you generate the keystore with:

```powershell
-storepass MySecret123
-keypass MySecret123
```

then the application must use the same password value:

```powershell
$env:APP_INTERNAL_JWT_KEYSTORE_PASSWORD = "MySecret123"
```

## Option 1: Generate a new PFX directly with keytool

Open PowerShell in the project root and run:

```powershell
keytool -genkeypair ^
  -alias bff-internal-jwt ^
  -keyalg RSA ^
  -keysize 2048 ^
  -sigalg SHA256withRSA ^
  -validity 3650 ^
  -keystore src/main/resources/keys/bff-jwt-keystore.pfx ^
  -storetype PKCS12 ^
  -storepass changeit ^
  -keypass changeit ^
  -dname "CN=BFF Internal JWT, OU=Dev, O=Example, L=Local, ST=Local, C=US"
```

Notes:

- `-alias` must match `app.internal-jwt.key-alias`
- `-storepass` is the password you are assigning to the `.pfx` file
- In this project, the same password is used for the keystore and key entry
- `-storepass` and `-keypass` should use the same value
- that same value must be provided to the app through `APP_INTERNAL_JWT_KEYSTORE_PASSWORD`

## Option 2: Convert an existing certificate/key into PFX

If you already have a certificate and private key, you can create a `.pfx` with OpenSSL.

Example:

```powershell
openssl pkcs12 -export ^
  -out bff-jwt-keystore.pfx ^
  -inkey private.key ^
  -in certificate.crt ^
  -name bff-internal-jwt
```

OpenSSL will prompt for an export password.

That export password becomes the `.pfx` password.

Example prompt flow:

```text
Enter Export Password:
Verifying - Enter Export Password:
```

If you enter `MySecret123`, then the app must use:

```powershell
$env:APP_INTERNAL_JWT_KEYSTORE_PASSWORD = "MySecret123"
```

Then copy the generated file to:

```text
src/main/resources/keys/bff-jwt-keystore.pfx
```

## Option 3: Generate everything with OpenSSL

If you do not already have a private key and certificate, you can create both with OpenSSL and then export them to `.pfx`.

### Step 1: Generate an RSA private key

```powershell
openssl genrsa -out private.key 2048
```

### Step 2: Generate a self-signed certificate

```powershell
openssl req -new -x509 ^
  -key private.key ^
  -out certificate.crt ^
  -days 3650 ^
  -subj "/CN=BFF Internal JWT/OU=Dev/O=Example/L=Local/ST=Local/C=US"
```

### Step 3: Export to PFX

```powershell
openssl pkcs12 -export ^
  -out src/main/resources/keys/bff-jwt-keystore.pfx ^
  -inkey private.key ^
  -in certificate.crt ^
  -name bff-internal-jwt
```

OpenSSL will ask for the export password.

That password is the one your application must use for `APP_INTERNAL_JWT_KEYSTORE_PASSWORD`.

Notes:

- `-name bff-internal-jwt` must match `app.internal-jwt.key-alias`
- the export password is the `.pfx` password
- after export, place the file at `src/main/resources/keys/bff-jwt-keystore.pfx` if you did not already export there directly

## Check the PFX contents

To list the entries inside the keystore:

```powershell
keytool -list -v -keystore src/main/resources/keys/bff-jwt-keystore.pfx -storetype PKCS12
```

Confirm:

- the alias exists
- the entry type is a key entry
- the certificate uses an RSA public key

## Configure the application

Set the password in Windows before starting the app.

PowerShell example for the current session:

```powershell
$env:APP_INTERNAL_JWT_KEYSTORE_PASSWORD = "changeit"
```

If you generated the `.pfx` with a different password, replace `changeit` with that value.

Example:

```powershell
$env:APP_INTERNAL_JWT_KEYSTORE_PASSWORD = "MySecret123"
```

For a persistent Windows user-level environment variable:

```powershell
[System.Environment]::SetEnvironmentVariable("APP_INTERNAL_JWT_KEYSTORE_PASSWORD", "MySecret123", "User")
```

After setting a persistent environment variable, open a new terminal before starting the app.

The app configuration should line up like this:

```yaml
app:
  internal-jwt:
    keystore-location: classpath:keys/bff-jwt-keystore.pfx
    keystore-password: ${APP_INTERNAL_JWT_KEYSTORE_PASSWORD:changeit}
    key-alias: ${APP_INTERNAL_JWT_KEY_ALIAS:bff-internal-jwt}
```

## Sample keystore file URLs

The `app.internal-jwt.keystore-location` value is a Spring `Resource` location.

Common examples:

### 1. Classpath location inside this project

Use this when the `.pfx` file is packaged with the application:

```yaml
app:
  internal-jwt:
    keystore-location: classpath:keys/bff-jwt-keystore.pfx
```

This maps to the project file:

```text
src/main/resources/keys/bff-jwt-keystore.pfx
```

### 2. Absolute file path on Windows

Use this when the `.pfx` file is stored outside the application package:

```yaml
app:
  internal-jwt:
    keystore-location: file:C:/apps/cloud-gateway/keys/bff-jwt-keystore.pfx
```

Another example:

```yaml
app:
  internal-jwt:
    keystore-location: file:D:/secure/keys/bff-jwt-keystore.pfx
```

### 3. Environment-variable-driven file path

Useful for Azure DevOps or environment-specific deployments:

```yaml
app:
  internal-jwt:
    keystore-location: file:${APP_INTERNAL_JWT_KEYSTORE_PATH:C:/apps/cloud-gateway/keys/bff-jwt-keystore.pfx}
```

Example PowerShell value:

```powershell
$env:APP_INTERNAL_JWT_KEYSTORE_PATH = "C:/apps/cloud-gateway/keys/bff-jwt-keystore.pfx"
```

### 4. Example with all values together

```yaml
app:
  internal-jwt:
    keystore-location: file:C:/apps/cloud-gateway/keys/bff-jwt-keystore.pfx
    keystore-password: ${APP_INTERNAL_JWT_KEYSTORE_PASSWORD}
    key-alias: ${APP_INTERNAL_JWT_KEY_ALIAS:bff-internal-jwt}
```

Notes:

- for Windows file locations, use forward slashes in Spring config
- `classpath:` is best when the keystore is bundled with the app
- `file:` is best when operations teams manage the keystore outside the app package

## Troubleshooting

### `keytool` is not recognized

Use a full JDK installation and ensure its `bin` folder is on `PATH`.

### Alias not found

Make sure the alias in the `.pfx` matches `app.internal-jwt.key-alias`.

### Wrong password

Make sure the password used in `-storepass` / `-keypass` when generating the `.pfx` matches `APP_INTERNAL_JWT_KEYSTORE_PASSWORD`.

### App fails to load RSA key

Make sure the keystore contains a private key entry and that the certificate is RSA-based.

## Convert PFX to Base64 for Azure DevOps

If it is easier to store the keystore as a secret variable in Azure DevOps, you can convert the `.pfx` file to Base64.

Important:

- Base64 is encoding, not encryption
- store the Base64 value as a secret variable
- store the keystore password in a separate secret variable

### Convert `.pfx` to Base64 on Windows

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("src/main/resources/keys/bff-jwt-keystore.pfx")) | Set-Content bff-jwt-keystore.base64
```

This creates a text file containing the Base64 value.

If you want to print the Base64 value directly to the console instead of saving it to a file, use:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("src/main/resources/keys/bff-jwt-keystore.pfx"))
```

You can copy that value into an Azure DevOps secret variable such as:

- `BFF_JWT_PFX_BASE64`

### Decode Base64 back into `.pfx` during release

In your Azure DevOps release or pipeline step, decode the secret back into a file:

```powershell
[IO.File]::WriteAllBytes("bff-jwt-keystore.pfx", [Convert]::FromBase64String("$(BFF_JWT_PFX_BASE64)"))
```

If you already have the Base64 value in a PowerShell variable, you can decode it like this:

```powershell
$base64 = "PASTE_BASE64_VALUE_HERE"
[IO.File]::WriteAllBytes("bff-jwt-keystore.pfx", [Convert]::FromBase64String($base64))
```

If the Base64 value is already copied to the Windows clipboard, decode directly from clipboard like this:

```powershell
[IO.File]::WriteAllBytes("bff-jwt-keystore.pfx", [Convert]::FromBase64String((Get-Clipboard)))
```

To decode from clipboard straight into the project resource location:

```powershell
[IO.File]::WriteAllBytes("src/main/resources/keys/bff-jwt-keystore.pfx", [Convert]::FromBase64String((Get-Clipboard)))
```

### Provide the password separately

Keep the keystore password in another secret variable, for example:

- `APP_INTERNAL_JWT_KEYSTORE_PASSWORD`

Then make sure the application uses that same password value when loading the keystore.

### Recommended Azure DevOps secret variables

- `BFF_JWT_PFX_BASE64`
- `APP_INTERNAL_JWT_KEYSTORE_PASSWORD`
- `APP_INTERNAL_JWT_KEY_ALIAS` if you want the alias configurable

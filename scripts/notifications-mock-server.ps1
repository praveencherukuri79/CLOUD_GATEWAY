Add-Type -AssemblyName System.Web

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add('http://localhost:8083/')
$listener.Start()

Write-Host 'notifications mock server listening on http://localhost:8083'

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        $request = $context.Request
        $response = $context.Response

        $path = $request.Url.AbsolutePath
        $authorization = $request.Headers['Authorization']
        $appUser = $request.Headers['X-APP-User']
        $correlationId = $request.Headers['X-Correlation-Id']

        if ($path -match '^/notifications/(\d+)$') {
            $id = $matches[1]
            $payload = @{
                id = [int]$id
                service = 'notifications-mock'
                message = 'response from custom notification service'
                path = $path
                authorization = $authorization
                appUser = $appUser
                correlationId = $correlationId
                proxyMode = 'custom'
            } | ConvertTo-Json -Depth 5

            $buffer = [System.Text.Encoding]::UTF8.GetBytes($payload)
            $response.StatusCode = 200
            $response.ContentType = 'application/json'
            $response.ContentEncoding = [System.Text.Encoding]::UTF8
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
        }
        else {
            $payload = @{ error = 'Not Found'; path = $path } | ConvertTo-Json
            $buffer = [System.Text.Encoding]::UTF8.GetBytes($payload)
            $response.StatusCode = 404
            $response.ContentType = 'application/json'
            $response.ContentEncoding = [System.Text.Encoding]::UTF8
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
        }

        if ($correlationId) {
            $response.Headers['X-Correlation-Id'] = $correlationId
        }

        $response.Close()
    }
}
finally {
    $listener.Stop()
    $listener.Close()
}
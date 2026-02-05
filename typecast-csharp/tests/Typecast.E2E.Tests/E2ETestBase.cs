using DotNetEnv;

namespace Typecast.E2E.Tests;

/// <summary>
/// Base class for E2E tests that provides environment configuration.
/// </summary>
public abstract class E2ETestBase : IDisposable
{
    protected readonly TypecastClient Client;
    protected readonly string ApiKey;

    static E2ETestBase()
    {
        // Load .env file from the test project directory
        var envPath = FindEnvFile();
        if (!string.IsNullOrEmpty(envPath))
        {
            Env.Load(envPath);
        }
    }

    protected E2ETestBase()
    {
        ApiKey = Environment.GetEnvironmentVariable("TYPECAST_API_KEY") 
            ?? throw new InvalidOperationException(
                "TYPECAST_API_KEY environment variable is not set. " +
                "Please create a .env file in the test project directory or set the environment variable.");
        
        Client = new TypecastClient(new TypecastClientConfig
        {
            ApiKey = ApiKey,
            TimeoutSeconds = 60 // Longer timeout for E2E tests
        });
    }

    private static string? FindEnvFile()
    {
        // Try to find .env file in various locations
        var currentDir = Directory.GetCurrentDirectory();
        
        // Check current directory
        var envPath = Path.Combine(currentDir, ".env");
        if (File.Exists(envPath)) return envPath;
        
        // Check parent directories up to 5 levels
        var dir = new DirectoryInfo(currentDir);
        for (var i = 0; i < 5 && dir?.Parent != null; i++)
        {
            dir = dir.Parent;
            envPath = Path.Combine(dir.FullName, ".env");
            if (File.Exists(envPath)) return envPath;
            
            // Also check tests/Typecast.E2E.Tests/.env
            envPath = Path.Combine(dir.FullName, "tests", "Typecast.E2E.Tests", ".env");
            if (File.Exists(envPath)) return envPath;
        }
        
        return null;
    }

    public void Dispose()
    {
        Client.Dispose();
        GC.SuppressFinalize(this);
    }
}

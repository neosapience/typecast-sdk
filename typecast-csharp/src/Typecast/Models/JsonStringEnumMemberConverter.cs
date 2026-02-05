using System.Reflection;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// JSON converter that uses JsonPropertyName attribute for enum serialization.
/// </summary>
public class JsonStringEnumMemberConverter : JsonConverterFactory
{
    /// <inheritdoc />
    public override bool CanConvert(Type typeToConvert) => typeToConvert.IsEnum;

    /// <inheritdoc />
    public override JsonConverter CreateConverter(Type typeToConvert, JsonSerializerOptions options)
    {
        var converterType = typeof(JsonStringEnumMemberConverterInner<>).MakeGenericType(typeToConvert);
        return (JsonConverter)Activator.CreateInstance(converterType)!;
    }

    private class JsonStringEnumMemberConverterInner<T> : JsonConverter<T> where T : struct, Enum
    {
        private readonly Dictionary<T, string> _enumToString = new();
        private readonly Dictionary<string, T> _stringToEnum = new();

        public JsonStringEnumMemberConverterInner()
        {
            var type = typeof(T);
            var values = Enum.GetValues(type);
            foreach (var value in values)
            {
                var enumValue = (T)value;
                var memberInfo = type.GetMember(value.ToString()!).FirstOrDefault();
                var attribute = memberInfo?.GetCustomAttribute<JsonPropertyNameAttribute>();
                var name = attribute?.Name ?? value.ToString()!.ToLowerInvariant();
                
                _enumToString[enumValue] = name;
                _stringToEnum[name] = enumValue;
            }
        }

        public override T Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
        {
            var stringValue = reader.GetString();
            if (stringValue != null && _stringToEnum.TryGetValue(stringValue, out var enumValue))
            {
                return enumValue;
            }
            
            throw new JsonException($"Unable to convert \"{stringValue}\" to enum type {typeof(T).Name}");
        }

        public override void Write(Utf8JsonWriter writer, T value, JsonSerializerOptions options)
        {
            if (_enumToString.TryGetValue(value, out var stringValue))
            {
                writer.WriteStringValue(stringValue);
            }
            else
            {
                writer.WriteStringValue(value.ToString().ToLowerInvariant());
            }
        }
    }
}

Gem::Specification.new do |spec|
  spec.name = "typecast-ruby"
  spec.version = "0.1.6"
  spec.authors = ["Neosapience"]
  spec.email = ["support@neosapience.com"]
  spec.summary = "Official Ruby SDK for the Typecast Text-to-Speech API"
  spec.description = "A Ruby client for Typecast TTS, timestamps, voices, subscription, and quick cloning APIs."
  spec.homepage = "https://github.com/neosapience/typecast-sdk/tree/main/typecast-ruby"
  spec.license = "MIT"
  spec.required_ruby_version = ">= 2.6"

  spec.files = Dir["lib/**/*", "LICENSE", "README.md", "THIRD-PARTY-LICENSES.md"]
  spec.require_paths = ["lib"]

  spec.add_development_dependency "minitest", "~> 5.0"
  spec.add_development_dependency "rake", "~> 13.0"
end

Pod::Spec.new do |s|
  s.name         = "CryptoModule"
  s.version      = "1.0.0"
  s.summary      = "A React Native crypto module for AES-256-CBC file decryption"
  s.description  = "A React Native module for AES-256-CBC file decryption on iOS and Android"
  s.homepage     = "https://github.com/Naveedkhan028/react-native-crypto-module"
  s.license      = { :type => "MIT" }
  s.author       = { "Navid" => "Naveedkhan028@gmail.com" }
  s.platform     = :ios, "12.0"
  s.source       = { :path => "." }
  s.source_files = "*.{h,m,mm}"
  s.requires_arc = true

  s.dependency "React-Core"
end
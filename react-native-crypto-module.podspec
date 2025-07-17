require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name         = "react-native-crypto-module"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.description  = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.author       = package["author"]
  s.platform     = :ios, "12.0"
  s.source       = { :git => "https://github.com/Naveedkhan028/react-native-crypto-module.git", :tag => "#{s.version}" }
  s.source_files = "ios/**/*.{h,m,mm}"
  s.requires_arc = true

  s.dependency "React-Core"
end
@echo off
cd /d "D:\develop\idea\idea\idea_workspace\AgentEngine"

REM 设置类路径
set CLASSPATH=agent-business\target\classes
set CLASSPATH=%CLASSPATH%;agent-common\target\classes

REM 添加Spring Boot和Jackson依赖
set CLASSPATH=%CLASSPATH%;C:\Users\%USERNAME%\.m2\repository\org\springframework\boot\spring-boot\3.3.5\spring-boot-3.3.5.jar
set CLASSPATH=%CLASSPATH%;C:\Users\%USERNAME%\.m2\repository\com\fasterxml\jackson\core\jackson-databind\2.18.1\jackson-databind-2.18.1.jar
set CLASSPATH=%CLASSPATH%;C:\Users\%USERNAME%\.m2\repository\com\fasterxml\jackson\core\jackson-core\2.18.1\jackson-core-2.18.1.jar
set CLASSPATH=%CLASSPATH%;C:\Users\%USERNAME%\.m2\repository\com\fasterxml\jackson\core\jackson-annotations\2.18.1\jackson-annotations-2.18.1.jar
set CLASSPATH=%CLASSPATH%;C:\Users\%USERNAME%\.m2\repository\org\projectlombok\lombok\1.18.34\lombok-1.18.34.jar
set CLASSPATH=%CLASSPATH%;C:\Users\%USERNAME%\.m2\repository\org\slf4j\slf4j-api\2.0.16\slf4j-api-2.0.16.jar

echo Running MCP JSON Parser Demo...
java -cp "%CLASSPATH%" com.agentengine.skill.parser.McpJsonParserDemo

pause

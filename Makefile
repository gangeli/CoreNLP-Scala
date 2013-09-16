JAVAC=javac
SCALAC=scalac

SRC=src
SOURCES = $(wildcard src/edu/stanford/nlp/*.scala)
TEST_SRC=test/src
LIB=lib
BUILD=classes
TEST_BUILD=test/classes

JAVANLP=${JAVANLP_HOME}/projects/core/classes:${JAVANLP_HOME}/projects/more/lib/BerkeleyParser.jar:${JAVANLP_HOME}/projects/core/lib/joda-time.jar:${JAVANLP_HOME}/projects/core/lib/jollyday-0.4.7.jar

default:
	mkdir -p $(BUILD)
	$(SCALAC) -cp $(JAVANLP) -d $(BUILD) `find $(SRC) -name "*.scala"`

clean:
	rm -r $(BUILD)


cmd:
	@echo "scala -J-Xmx4G -cp $(JAVANLP):$(BUILD)":${HOME}/lib/corenlp-models.jar

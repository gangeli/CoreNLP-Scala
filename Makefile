JAVAC=javac
SCALAC=scalac

SRC=src
SOURCES = $(wildcard src/edu/stanford/nlp/*.scala)
TEST_SRC=test/src
LIB=lib
BUILD=classes
TEST_BUILD=test/classes

#
# To Build:
#  1. Set CORENLP_HOME to the root of CoreNLP
#  2. [optional] Set BERKELEY to the path to the Berkeley parser
#  3. Build using either 'make stanford' or 'make berkeley' (if the Berkeley parser is configured)
#
CORENLP=${CORENLP_HOME}/classes:${CORENLP_HOME}/lib/joda-time.jar:${CORENLP_HOME}/lib/jollyday-0.4.7.jar
BERKELEY=${CORENLP_HOME}/../more/lib/BerkeleyParser.jar

berkeley: stanford
	$(SCALAC) -cp $(CORENLP):${BERKELEY} -d $(BUILD) `find $(SRC) -name "*.scala"`

stanford: ${SOURCES}
	mkdir -p $(BUILD)
	sed -r -e 's/BerkeleyUtil.berkeleyParser/throw new IllegalStateException("Project compiled without the Berkeley parser!")/g' ${SRC}/edu/stanford/nlp/NLP.scala > /tmp/NLP_stanfordonly.scala
	$(SCALAC) -cp $(CORENLP) -d $(BUILD) `find $(SRC) -name "*.scala" ! -name "*Berkeley.scala" ! -name "NLP.scala"` /tmp/NLP_stanfordonly.scala
	rm /tmp/NLP_stanfordonly.scala

default: stanford

clean:
	rm -r $(BUILD)


cmd:
	@echo "scala -J-Xmx4G -cp $(JAVANLP):$(BUILD)":${HOME}/lib/corenlp-models.jar

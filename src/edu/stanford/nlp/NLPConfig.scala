package edu.stanford.nlp

import edu.stanford.nlp.pipeline.DefaultPaths._

object NLPConfig {
  object parse {
    var model:String = DEFAULT_PARSER_MODEL
  }

  object pos {
    var model:String = DEFAULT_POS_MODEL
  }
  
  object ner {
    var model:String = DEFAULT_NER_CONLL_MODEL
    var aux:String   = DEFAULT_NER_MUC_MODEL
  }

  object classify {
    var tolerance:Double = 1e-5
    var iterations:Double = 40
  }

  object optimize {
    var tolerance:Double = 1e-5
    var wiggle:Double = 1e-5
    var algorithm = "LBFGS" // | braindead | ...
  }

  object truecase {
    var model:String = "edu/stanford/nlp/models/truecase/truecasing.fast.caseless.qn.ser.gz"
    var disambiguation_list:String = "edu/stanford/nlp/models/truecase/MixDisambiguation.list"
    var bias:String = "INIT_UPPER:-0.7,UPPER:-0.7,O:0"
  }

  def caseless:Unit = {
    parse.model = "edu/stanford/nlp/models/lexparser/englishPCFG.caseless.ser.gz"
    pos.model = "edu/stanford/nlp/models/pos-tagger/english-caseless-left3words-distsim.tagger"
    ner.model = "edu/stanford/nlp/models/ner/english.conll.4class.caseless.distsim.crf.ser.gz"
    ner.aux = "edu/stanford/nlp/models/ner/english.muc.7class.caseless.distsim.crf.ser.gz"
  }

  var numThreads = Runtime.getRuntime().availableProcessors();
}

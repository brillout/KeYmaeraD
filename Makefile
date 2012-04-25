BACKENDSOURCES= types.scala rational.scala logicutil.scala arithmetic.scala \
	parse.scala keyparse.scala printing.scala \
	nodes.scala mathematicautil.scala \
	rules.scala procedures.scala jobs.scala \
	DLprover.scala 

FRONTENDSOURCES= frontend.scala frontactor.scala GUI/guifrontend.scala \
	tactics.scala 	

TESTINGSOURCES= testing/examples.scala

LIBRARIES= .:$(JLINK)/JLink.jar:./commons-cli-1.2/commons-cli-1.2.jar

ifndef SCALAC
SCALAC= fsc
endif

OPTIONS=
ALLOPTIONS=${OPTIONS} -deprecation -unchecked

prover : frontend backend
.PHONY : prover

frontend : KeYmaeraD/FrontActor.class
.PHONY : frontend
backend : KeYmaeraD/Rules.class
.PHONY : backend

KeYmaeraD/FrontActor.class : specialoptions KeYmaeraD/Rules.class $(FRONTENDSOURCES)
	$(SCALAC) -classpath $(LIBRARIES) $(FRONTENDSOURCES) $(ALLOPTIONS)

KeYmaeraD/Rules.class : specialoptions $(BACKENDSOURCES)
	$(SCALAC) -classpath $(LIBRARIES) $(BACKENDSOURCES) $(ALLOPTIONS)

specialoptions :	
	$(SCALAC) -version 2>&1 | python specialoptions.py > specialoptions.tmp
	# Only make the real thing if the last line succeeded.
	mv specialoptions.tmp specialoptions

tests : prover $(TESTINGSOURCES)
	$(SCALAC) -classpath $(LIBRARIES) $(TESTINGSOURCES) $(ALLOPTIONS)

clean :
	rm -f specialoptions specialoptions.tmp
	rm -rf KeYmaeraD/
	$(SCALAC) -shutdown -verbose

# We should allow the tactics library to be compiled separately. 
#.PHONY : tacticlib
#tacticlib : KeYmaeraD/TacticLib/*.class
#KeYmaeraD/TacticLib/*.class : TacticLib/*.scala
#	$(SCALAC)  -classpath $(LIBRARIES) TacticLib/*.scala $(ALLOPTIONS)

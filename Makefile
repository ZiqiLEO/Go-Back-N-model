JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $*.java

CLASSES = \
	packet.java\
	sender.java \
	receiver.java


	
default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class


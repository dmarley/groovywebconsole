import org.codehaus.groovy.control.MultipleCompilationErrorsException
import com.google.apphosting.api.ApiProxy

def scriptText = request.getParameter("script") ?: "'The received script was null.'"

def encoding = 'UTF-8'
def stream = new ByteArrayOutputStream()
def printStream = new PrintStream(stream, true, encoding)

def stacktrace = new StringWriter()
def errWriter = new PrintWriter(stacktrace)

def binding = new Binding([out: printStream])

def originalOut = System.out
def originalErr = System.err

System.setOut(printStream)
System.setErr(printStream)

def env = ApiProxy.getCurrentEnvironment()
ApiProxy.clearEnvironmentForCurrentThread()
def result = ""
try {
	result = new GroovyShell(binding).evaluate(scriptText)
} catch (MultipleCompilationErrorsException e) {
	stacktrace.append(e.message - 'startup failed, Script1.groovy: ')
} catch (Throwable t) {
	sanitizeStacktrace(t)
	def cause = t
	while (cause = cause?.cause) {
		sanitizeStacktrace(cause)
	}
	t.printStackTrace(errWriter)
} finally {
    ApiProxy.setEnvironmentForCurrentThread(env)
    System.setOut(originalOut)
    System.setErr(originalErr)
}

response.contentType = "application/json"

out.println """{
	executionResult: "${escape(result)}",
 	outputText: "${escape(stream.toString(encoding))}",
 	stacktraceText: "${escape(stacktrace)}"
}"""

def escape(object) {
	object.toString().replaceAll(/\n/, /\\\n/).replaceAll(/"/, /\\"/)
}

def sanitizeStacktrace(t) {
    def filtered = [
        'com.google.', 'org.mortbay.',
        'java.', 'javax.', 'sun.', 
        'groovy.', 'org.codehaus.groovy.',
        'groovyx.gaelyk.', 'executor'
	]
    def trace = t.getStackTrace()
    def newTrace = []
    trace.each { stackTraceElement -> 
        if (filtered.every { !stackTraceElement.className.startsWith(it) }) {
            newTrace << stackTraceElement
        }
    }
    def clean = newTrace.toArray(newTrace as StackTraceElement[])
    t.stackTrace = clean
}
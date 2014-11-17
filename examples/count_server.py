# twistd -noy count_server.py

from twisted.python.log import err, msg
from twisted.protocols import amp
from twisted.protocols.amp import AMP
from twisted.internet import reactor
from twisted.internet.protocol import Factory
from twisted.internet.endpoints import TCP4ServerEndpoint
from twisted.application.service import Application
from twisted.application.internet import StreamServerEndpointService

class Count(amp.Command):
    arguments = [('n', amp.Integer())]
    response = [('oki', amp.Integer()),
                ('oks', amp.String()),
                ('okde', amp.Unicode()),
                ('okb', amp.Boolean()),
                ('okdo', amp.Float())]

class Counter(amp.AMP):
    @Count.responder
    def count(self, n):
        print 'received:', n
        n += 1

        maxcount = 11
        if (n < maxcount):
            print 'sending:', n
            d = self.callRemote(Count, n=n)
            d.addCallback(lambda resp: msg("response:", resp))
            d.addErrback(err, 'callRemote failed')
        else:
            reactor.stop()

        return { 'oki': 1, 'oks': '2', 'okde': unicode('3'), 'okb': True,
                 'okdo': 5.123 }

    def connectionLost(self, reason):
        print 'Client closed connection!'


application = Application('Count Server')
endpoint = TCP4ServerEndpoint(reactor, 7113)
factory = Factory()
factory.protocol = Counter
service = StreamServerEndpointService(endpoint, factory)
service.setServiceParent(application)

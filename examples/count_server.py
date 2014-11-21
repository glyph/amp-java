# twistd -noy count_server.py

from decimal import Decimal
from datetime import datetime
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
                ('oku', amp.Unicode()),
                ('okb', amp.Boolean()),
                ('okf', amp.Float()),
                ('okd', amp.Decimal()),
                ('okt', amp.DateTime()),
                ('okl1', amp.ListOf(amp.Integer())),
                ('okl2', amp.ListOf(amp.ListOf(amp.String()))),
                ('okla', amp.AmpList([('a', amp.Integer()),
                                      ('b', amp.Unicode())]))]

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

        return { 'oki': 1, 'oks': '2', 'oku': u'3', 'okb': True,
                 'okf': 5.123, 'okd': Decimal('3') / Decimal('4'),
                 'okt': datetime.now(amp.utc),
                 'okl1': [4, 5, 6],
                 'okl2': [['str01','str02'], ['str03','str04','str05']],
                 'okla': [{'a': 7, 'b': u'hello'}, {'a': 9, 'b': u'goodbye'}]
             }

    def connectionLost(self, reason):
        print 'Client closed connection!'


application = Application('Count Server')
endpoint = TCP4ServerEndpoint(reactor, 7113)
factory = Factory()
factory.protocol = Counter
service = StreamServerEndpointService(endpoint, factory)
service.setServiceParent(application)

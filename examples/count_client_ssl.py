# python count_client_ssl.py

from OpenSSL import SSL
from decimal import Decimal
from datetime import datetime
from sys import stdout, exit
from twisted.internet import reactor, ssl
from twisted.internet.protocol import ClientFactory
from twisted.protocols import amp
from twisted.python.log import startLogging, err, msg

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

        maxcount = 10
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

    def connectionMade(self):
        self.callRemote(Count, n=1)

class CountFactory(ClientFactory):
    protocol = Counter

    def clientConnectionFailed(self, connector, reason):
        print "Connection failed - goodbye!"
        reactor.stop()

    def clientConnectionLost(self, connector, reason):
        print "Connection lost - goodbye!"

if __name__ == '__main__':
    startLogging(stdout)
    factory = CountFactory()

    reactor.connectSSL('localhost', 7113, factory, ssl.ClientContextFactory())
    reactor.run()

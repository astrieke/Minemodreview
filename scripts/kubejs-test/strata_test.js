// Testskript für die KubeJS-Anbindung von Strata Bridge (scripts/kubejs-test.sh).
ServerEvents.loaded(event => {
  const epochs = StrataAPI.epochs(event.server).size()
  const sample = Strata.coreSample('strata_epochs:spore_age')
  const ok = epochs > 0 && sample.getCount() == 1 && Strata.epochOf(event.server.overworld()) == ''
  console.info('[Strata-KubeJS] Epochen=' + epochs + ', Bohrkern=' + sample + ' -> ' + (ok ? 'OK' : 'FEHLER'))
})
NativeEvents.onEvent('dev.strata.core.api.CodexNodeUnlockedEvent', event => {
  console.info('[Strata-KubeJS] Freigeschaltet: ' + event.getNode())
})

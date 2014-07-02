package testasm;

import java.util.List;
import java.util.ArrayList;

import org.pmw.tinylog.Logger;

public class SpeculativeList {
  private List<Object> list;
  private int[] loads;
  private int[] stores;
  private List<Object>[] shadowBuffer;
  public SpeculativeList(List<Object> list, int count) {
    this.list = list;
    loads = new int[list.size()];
    stores = new int[list.size()];
    shadowBuffer = new List[count];
    shadowBuffer[0] = (List<Object>) ((ArrayList<Object>) list).clone();
  }

  public boolean add(Object e) {
    return list.add(e);
  }

  public synchronized Object speculativeLoad(int id, int index) throws SpeculationException {
    if (loads[index] < id) {
      loads[index] = id;
    }

    Object val = list.get(index);

    if (stores[index] <= id) {
      shadowBuffer[id] = (List<Object>) ((ArrayList<Object>) list).clone();
      return val;
    }

    throw new SpeculationException();
  }

  public synchronized void speculativeStore(int id, int index, Object value) throws SpeculationException {
    if (stores[index] > id) {
      throw new SpeculationException();
    }

    stores[index] = id;

    list.set(index, value);

    if (loads[index] > id) {
      throw new SpeculationException();
    }

    shadowBuffer[id] = (List<Object>) ((ArrayList<Object>) list).clone();
  }

  public synchronized List<Object> list() throws Exception {
    return this.list;
  }

  public synchronized void setList(List<Object> list) {
    this.list = list;
  }

  public synchronized void clearVectors() {
    loads = new int[list.size()];
    stores = new int[list.size()];
  }

  public synchronized void rollback(int id) {
    for (int j = id; j >= 0; j -= 1) {
      if (shadowBuffer[j] != null) {
        Logger.trace("id: {0}, j: {1}, i: {2}", id, j, shadowBuffer[j].get(4));
        list = shadowBuffer[j];
        break;
      }
    }
  }
}
